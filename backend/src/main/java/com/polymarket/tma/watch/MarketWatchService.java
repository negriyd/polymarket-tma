package com.polymarket.tma.watch;

import com.polymarket.tma.common.ApiException;
import com.polymarket.tma.market.client.GammaClient;
import com.polymarket.tma.market.dto.MarketDto;
import com.polymarket.tma.market.i18n.MarketLocale;
import com.polymarket.tma.market.i18n.MarketTranslationService;
import com.polymarket.tma.share.ShareService;
import com.polymarket.tma.share.TelegramBotClient;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Tells users about markets they follow — bets, open predictions, saved markets: when the chance of
 * their side moved {@value #MOVE_POINTS} points since we last told them (or first saw it), and once
 * when a market they bet on or predicted closes within a day. At most {@value #DAILY_LIMIT} such
 * messages per user per 24 hours; one message covers every market that changed in a run.
 */
@Service
public class MarketWatchService {

    private static final Logger log = LoggerFactory.getLogger(MarketWatchService.class);
    static final int MOVE_POINTS = 10;
    static final BigDecimal MOVE = BigDecimal.valueOf(MOVE_POINTS, 2);
    static final int DAILY_LIMIT = 3;
    static final Duration CLOSING_WINDOW = Duration.ofHours(24);
    /** Lines per message; the rest waits for the next run. */
    static final int MAX_LINES = 6;
    private static final int GAMMA_BATCH = 40;
    private static final long PAUSE_MS = 60;
    private static final String ACCEPTED = "status IN ('matched', 'live', 'delayed', 'unmatched')";

    private final JdbcTemplate jdbc;
    private final GammaClient gamma;
    private final TelegramBotClient bot;
    private final MarketTranslationService translations;

    public MarketWatchService(JdbcTemplate jdbc, GammaClient gamma, TelegramBotClient bot,
                              MarketTranslationService translations) {
        this.jdbc = jdbc;
        this.gamma = gamma;
        this.bot = bot;
        this.translations = translations;
    }

    /** A followed market; {@code committed} = the user bet on or predicted it (closing reminders). */
    record Watch(long userId, String conditionId, Integer outcomeIndex, String tokenId, boolean committed) {}

    record State(BigDecimal lastPrice, boolean closingReminded) {}

    /** The user's side of a market right now. */
    record Item(String conditionId, String marketId, String question, String outcome, BigDecimal price,
                Instant endDate, boolean committed) {
        Item withQuestion(String q) {
            return new Item(conditionId, marketId, q, outcome, price, endDate, committed);
        }
    }

    enum Kind { MOVED, CLOSING }

    record Alert(Kind kind, Item item, BigDecimal from) {}

    /** Alerts for one user's items, and the items seen for the first time (to store as a baseline). */
    record Decision(List<Alert> alerts, List<Item> baselines) {}

    static Decision decide(List<Item> items, Map<String, State> states, Instant now) {
        List<Alert> alerts = new ArrayList<>();
        List<Item> baselines = new ArrayList<>();
        for (Item it : items) {
            State s = states.get(it.conditionId());
            if (s == null) {
                baselines.add(it);
            } else if (it.price().subtract(s.lastPrice()).abs().compareTo(MOVE) >= 0) {
                alerts.add(new Alert(Kind.MOVED, it, s.lastPrice()));
                continue;
            }
            boolean closing = it.committed() && it.endDate() != null && it.endDate().isAfter(now)
                    && !it.endDate().isAfter(now.plus(CLOSING_WINDOW));
            if (closing && (s == null || !s.closingReminded())) {
                alerts.add(new Alert(Kind.CLOSING, it, null));
            }
        }
        return new Decision(alerts.size() > MAX_LINES ? alerts.subList(0, MAX_LINES) : alerts, baselines);
    }

    public int run() {
        List<Watch> watches = jdbc.query("""
                SELECT x.user_id, x.condition_id, x.outcome_index, x.token_id, x.committed, x.prio
                FROM app_user u JOIN (
                    SELECT user_id, condition_id, NULL::SMALLINT AS outcome_index, token_id, TRUE AS committed, 1 AS prio
                    FROM order_audit WHERE side = 'BUY' AND condition_id IS NOT NULL AND {accepted}
                       AND created_at > NOW() - INTERVAL '120 days'
                    UNION ALL
                    SELECT user_id, condition_id, outcome_index, NULL, TRUE, 2 FROM prediction WHERE resolved_at IS NULL
                    UNION ALL
                    SELECT user_id, condition_id, NULL, NULL, FALSE, 3 FROM favorite_market
                ) x ON x.user_id = u.id
                WHERE u.allows_messages
                ORDER BY x.user_id, x.prio
                """.replace("{accepted}", ACCEPTED), (rs, i) -> new Watch(rs.getLong("user_id"), rs.getString("condition_id"),
                rs.getObject("outcome_index", Integer.class), rs.getString("token_id"),
                rs.getBoolean("committed")));
        if (watches.isEmpty()) {
            return 0;
        }
        Map<String, MarketDto> markets = fetchOpen(watches.stream().map(Watch::conditionId).distinct().toList());
        // Bet first, then prediction, then saved: the first row per (user, market) wins.
        Map<Long, Map<String, Watch>> byUser = new LinkedHashMap<>();
        for (Watch w : watches) {
            byUser.computeIfAbsent(w.userId(), k -> new LinkedHashMap<>()).putIfAbsent(w.conditionId(), w);
        }
        String botName = null;
        int sent = 0;
        for (var e : byUser.entrySet()) {
            long userId = e.getKey();
            try {
                List<Item> items = new ArrayList<>();
                for (Watch w : e.getValue().values()) {
                    Item it = item(w, markets.get(w.conditionId().toLowerCase()));
                    if (it != null) {
                        items.add(it);
                    }
                }
                if (items.isEmpty()) {
                    continue;
                }
                Decision d = decide(items, states(userId), Instant.now());
                for (Item b : d.baselines()) {
                    jdbc.update("""
                            INSERT INTO watch_state (user_id, condition_id, last_price) VALUES (?, ?, ?)
                            ON CONFLICT (user_id, condition_id) DO NOTHING
                            """, userId, b.conditionId(), b.price());
                }
                if (d.alerts().isEmpty() || usedToday(userId) >= DAILY_LIMIT) {
                    continue;
                }
                if (botName == null) {
                    botName = bot.username().block(Duration.ofSeconds(10));
                }
                if (send(userId, d.alerts(), botName)) {
                    sent++;
                }
            } catch (RuntimeException ex) {
                log.warn("Market watch for user {} failed: {}", userId, ex.toString());
            }
        }
        if (sent > 0) {
            log.info("Sent {} market watch messages", sent);
        }
        return sent;
    }

    private Map<String, MarketDto> fetchOpen(List<String> conditionIds) {
        Map<String, MarketDto> out = new HashMap<>();
        for (int i = 0; i < conditionIds.size(); i += GAMMA_BATCH) {
            List<String> batch = conditionIds.subList(i, Math.min(conditionIds.size(), i + GAMMA_BATCH));
            try {
                List<MarketDto> list = gamma.marketsByConditionIds(batch, null).block(Duration.ofSeconds(20));
                for (MarketDto m : list == null ? List.<MarketDto>of() : list) {
                    if (m.conditionId() != null && !Boolean.TRUE.equals(m.closed())) {
                        out.put(m.conditionId().toLowerCase(), m);
                    }
                }
            } catch (RuntimeException e) {
                log.warn("Market watch: Gamma batch failed: {}", e.toString());
            }
        }
        return out;
    }

    static Item item(Watch w, MarketDto m) {
        if (m == null || m.outcomePrices() == null || m.outcomePrices().isEmpty()) {
            return null;
        }
        int idx = 0;
        if (w.outcomeIndex() != null) {
            idx = w.outcomeIndex();
        } else if (w.tokenId() != null && m.clobTokenIds() != null && m.clobTokenIds().contains(w.tokenId())) {
            idx = m.clobTokenIds().indexOf(w.tokenId());
        }
        if (idx >= m.outcomePrices().size()) {
            return null;
        }
        BigDecimal price;
        try {
            price = new BigDecimal(m.outcomePrices().get(idx));
        } catch (NumberFormatException e) {
            return null;
        }
        String outcome = m.outcomes() != null && idx < m.outcomes().size() ? m.outcomes().get(idx) : "";
        return new Item(w.conditionId(), m.id(), m.question(), outcome, price, m.endDate(), w.committed());
    }

    private Map<String, State> states(long userId) {
        Map<String, State> out = new HashMap<>();
        jdbc.query("SELECT condition_id, last_price, closing_reminded FROM watch_state WHERE user_id = ?",
                rs -> {
                    out.put(rs.getString("condition_id"),
                            new State(rs.getBigDecimal("last_price"), rs.getBoolean("closing_reminded")));
                }, userId);
        return out;
    }

    private int usedToday(long userId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM bot_message WHERE user_id = ? AND kind = 'WATCH' "
                + "AND sent_at > NOW() - INTERVAL '24 hours'", Integer.class, userId);
        return n == null ? 0 : n;
    }

    private boolean send(long userId, List<Alert> alerts, String botName) {
        Map<String, Object> user = jdbc.queryForMap(
                "SELECT telegram_id, language_code FROM app_user WHERE id = ?", userId);
        String locale = MarketLocale.fromHeader((String) user.get("language_code"));
        WatchTexts.Texts t = WatchTexts.of(locale);
        List<Item> items = alerts.stream().map(Alert::item).toList();
        List<Item> localized = translations.translate(items, locale, Item::conditionId, Item::withQuestion)
                .block(Duration.ofSeconds(10));
        Map<String, String> titles = new HashMap<>();
        for (Item it : localized == null ? items : localized) {
            titles.put(it.conditionId(), it.question());
        }
        String html = text(alerts, titles, t, Instant.now());
        Item only = alerts.stream().map(Alert::item).distinct().count() == 1 ? alerts.get(0).item() : null;
        String button = only != null && only.marketId() != null && only.marketId().matches("\\d+")
                ? t.openMarket() : t.openApp();
        String param = button.equals(t.openMarket()) ? "m_" + only.marketId() + "_swatch" : "swatch";
        try {
            bot.sendMessage(((Number) user.get("telegram_id")).longValue(), html, button,
                    "https://t.me/" + botName + "?startapp=" + param).block(Duration.ofSeconds(15));
        } catch (ApiException e) {
            if (TelegramBotClient.FORBIDDEN.equals(e.getCode())) {
                log.info("User {} blocked the bot; messages off", userId);
                jdbc.update("UPDATE app_user SET allows_messages = FALSE WHERE id = ?", userId);
                return false;
            }
            throw e;
        }
        jdbc.update("INSERT INTO bot_message (user_id, kind) VALUES (?, 'WATCH')", userId);
        for (Alert a : alerts) {
            if (a.kind() == Kind.MOVED) {
                jdbc.update("UPDATE watch_state SET last_price = ?, last_alert_at = NOW(), updated_at = NOW() "
                        + "WHERE user_id = ? AND condition_id = ?", a.item().price(), userId, a.item().conditionId());
            } else {
                jdbc.update("""
                        INSERT INTO watch_state (user_id, condition_id, last_price, closing_reminded) VALUES (?, ?, ?, TRUE)
                        ON CONFLICT (user_id, condition_id) DO UPDATE SET closing_reminded = TRUE, updated_at = NOW()
                        """, userId, a.item().conditionId(), a.item().price());
            }
        }
        try {
            Thread.sleep(PAUSE_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return true;
    }

    static String text(List<Alert> alerts, Map<String, String> titles, WatchTexts.Texts t, Instant now) {
        StringBuilder moved = new StringBuilder();
        StringBuilder closing = new StringBuilder();
        for (Alert a : alerts) {
            Item it = a.item();
            String q = ShareService.escape(titles.getOrDefault(it.conditionId(), it.question()));
            String o = ShareService.escape("yes".equalsIgnoreCase(it.outcome()) ? t.yes()
                    : "no".equalsIgnoreCase(it.outcome()) ? t.no() : it.outcome());
            if (a.kind() == Kind.MOVED) {
                moved.append("\n").append(t.movedLine().replace("{q}", q).replace("{o}", o)
                        .replace("{from}", pct(a.from())).replace("{to}", pct(it.price())));
            } else {
                long hours = Math.max(1, Duration.between(now, it.endDate()).toHours());
                closing.append("\n").append(t.closingLine().replace("{q}", q).replace("{o}", o)
                        .replace("{to}", pct(it.price())).replace("{h}", String.valueOf(hours)));
            }
        }
        StringBuilder sb = new StringBuilder();
        if (!moved.isEmpty()) {
            sb.append(t.movedTitle()).append(moved);
        }
        if (!closing.isEmpty()) {
            sb.append(sb.isEmpty() ? "" : "\n\n").append(t.closingTitle()).append(closing);
        }
        return sb.toString();
    }

    static String pct(BigDecimal price) {
        return price.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toPlainString();
    }
}
