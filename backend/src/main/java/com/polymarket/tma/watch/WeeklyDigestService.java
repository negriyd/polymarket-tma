package com.polymarket.tma.watch;

import com.polymarket.tma.common.ApiException;
import com.polymarket.tma.market.MarketService;
import com.polymarket.tma.market.dto.MarketDto;
import com.polymarket.tma.market.dto.MarketListResponse;
import com.polymarket.tma.market.i18n.MarketLocale;
import com.polymarket.tma.market.i18n.MarketTranslationService;
import com.polymarket.tma.partner.AuthorService;
import com.polymarket.tma.share.ShareService;
import com.polymarket.tma.share.TelegramBotClient;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.IsoFields;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Monday message to everyone who forecast or bet in the last 30 days: their week's accuracy, their
 * place among this week's forecasters, and three hot markets in their language. Once per user per
 * ISO week.
 */
@Service
public class WeeklyDigestService {

    private static final Logger log = LoggerFactory.getLogger(WeeklyDigestService.class);
    private static final int HOT = 3;
    private static final long PAUSE_MS = 60;

    private final JdbcTemplate jdbc;
    private final TelegramBotClient bot;
    private final MarketService markets;
    private final MarketTranslationService translations;

    public WeeklyDigestService(JdbcTemplate jdbc, TelegramBotClient bot, MarketService markets,
                               MarketTranslationService translations) {
        this.jdbc = jdbc;
        this.bot = bot;
        this.markets = markets;
        this.translations = translations;
    }

    record Week(long correct, long total) {}

    /** "2026-W42": the dedupe key of a digest. */
    public static String weekRef(LocalDate day) {
        return day.get(IsoFields.WEEK_BASED_YEAR) + "-W" + String.format("%02d", day.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }

    /** 1-based place by correct predictions among those with enough resolved; null when not ranked. */
    static Integer rank(Week mine, List<Week> all, int minResolved) {
        if (mine == null || mine.total() < minResolved) {
            return null;
        }
        return 1 + (int) all.stream().filter(w -> w.total() >= minResolved && w.correct() > mine.correct()).count();
    }

    public int run() {
        String ref = weekRef(LocalDate.now(ZoneOffset.UTC));
        List<Map<String, Object>> users = jdbc.queryForList("""
                SELECT u.id, u.telegram_id, u.language_code FROM app_user u
                WHERE u.allows_messages
                  AND (EXISTS (SELECT 1 FROM prediction p WHERE p.user_id = u.id AND p.created_at > NOW() - INTERVAL '30 days')
                    OR EXISTS (SELECT 1 FROM order_audit o WHERE o.user_id = u.id AND o.created_at > NOW() - INTERVAL '30 days'))
                  AND NOT EXISTS (SELECT 1 FROM bot_message b WHERE b.user_id = u.id AND b.kind = 'WEEKLY' AND b.ref = ?)
                """, ref);
        if (users.isEmpty()) {
            return 0;
        }
        Map<Long, Week> weeks = new HashMap<>();
        jdbc.query("""
                SELECT user_id, COUNT(*) FILTER (WHERE correct) AS ok, COUNT(*) AS total
                FROM prediction WHERE resolved_at > NOW() - INTERVAL '7 days' GROUP BY user_id
                """, rs -> {
            weeks.put(rs.getLong("user_id"), new Week(rs.getLong("ok"), rs.getLong("total")));
        });
        List<Week> all = List.copyOf(weeks.values());
        int ranked = (int) all.stream().filter(w -> w.total() >= AuthorService.MIN_RESOLVED_WEEK).count();
        MarketListResponse hotList = markets.list(0, HOT, "volume24hr", false, null, null).block(Duration.ofSeconds(15));
        List<MarketDto> hot = hotList == null ? List.of() : hotList.items();
        Map<String, List<MarketDto>> hotByLocale = new HashMap<>();
        String botName = bot.username().block(Duration.ofSeconds(10));

        int sent = 0;
        for (Map<String, Object> u : users) {
            long userId = ((Number) u.get("id")).longValue();
            String locale = MarketLocale.fromHeader((String) u.get("language_code"));
            WatchTexts.Texts t = WatchTexts.of(locale);
            List<MarketDto> localHot = hotByLocale.computeIfAbsent(String.valueOf(locale), k -> {
                List<MarketDto> tr = translations.translateMarkets(hot, locale).block(Duration.ofSeconds(10));
                return tr == null ? hot : tr;
            });
            Week w = weeks.get(userId);
            String html = text(t, w, rank(w, all, AuthorService.MIN_RESOLVED_WEEK), ranked, localHot);
            try {
                bot.sendMessage(((Number) u.get("telegram_id")).longValue(), html, t.openApp(),
                        "https://t.me/" + botName + "?startapp=sweekly").block(Duration.ofSeconds(15));
                jdbc.update("INSERT INTO bot_message (user_id, kind, ref) VALUES (?, 'WEEKLY', ?) ON CONFLICT DO NOTHING",
                        userId, ref);
                sent++;
                Thread.sleep(PAUSE_MS);
            } catch (ApiException e) {
                if (TelegramBotClient.FORBIDDEN.equals(e.getCode())) {
                    jdbc.update("UPDATE app_user SET allows_messages = FALSE WHERE id = ?", userId);
                } else {
                    log.warn("Weekly digest for user {} failed: {}", userId, e.toString());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (RuntimeException e) {
                log.warn("Weekly digest for user {} failed: {}", userId, e.toString());
            }
        }
        log.info("Sent {} weekly digests ({})", sent, ref);
        return sent;
    }

    static String text(WatchTexts.Texts t, Week w, Integer rank, int ranked, List<MarketDto> hot) {
        StringBuilder sb = new StringBuilder(t.weeklyTitle()).append("\n\n");
        sb.append(w == null || w.total() == 0 ? t.weeklyNone()
                : t.weeklyAccuracy().replace("{c}", String.valueOf(w.correct())).replace("{t}", String.valueOf(w.total())));
        if (rank != null) {
            sb.append("\n").append(t.weeklyRank().replace("{r}", String.valueOf(rank)).replace("{n}", String.valueOf(ranked)));
        }
        if (!hot.isEmpty()) {
            sb.append("\n\n").append(t.weeklyHot());
            for (MarketDto m : hot) {
                String p = m.outcomePrices() == null || m.outcomePrices().isEmpty() ? null : m.outcomePrices().get(0);
                String pct;
                try {
                    pct = p == null ? "?" : MarketWatchService.pct(new BigDecimal(p));
                } catch (NumberFormatException e) {
                    pct = "?";
                }
                sb.append("\n").append(t.hotLine().replace("{q}", ShareService.escape(m.question())).replace("{p}", pct));
            }
        }
        return sb.toString();
    }
}
