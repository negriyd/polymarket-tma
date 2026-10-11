package com.polymarket.tma.results;

import com.polymarket.tma.common.ApiException;
import com.polymarket.tma.market.i18n.MarketLocale;
import com.polymarket.tma.market.i18n.MarketTranslationService;
import com.polymarket.tma.share.ShareService;
import com.polymarket.tma.share.TelegramBotClient;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Sends queued result messages through the bot: at most {@value #DAILY_LIMIT} messages per user per
 * 24 hours; when more results are waiting than that allows, the last message is a digest of the rest.
 * Users who didn't allow messages are skipped; a 403 (bot blocked) switches messages off for them.
 */
@Service
public class ResultNotifier {

    private static final Logger log = LoggerFactory.getLogger(ResultNotifier.class);
    static final int DAILY_LIMIT = 3;
    /** Stay well under Telegram's ~30 messages/second per bot. */
    private static final long PAUSE_MS = 60;

    private final JdbcTemplate jdbc;
    private final TelegramBotClient bot;
    private final MarketTranslationService translations;

    public ResultNotifier(JdbcTemplate jdbc, TelegramBotClient bot, MarketTranslationService translations) {
        this.jdbc = jdbc;
        this.bot = bot;
        this.translations = translations;
    }

    record Pending(long id, String kind, String conditionId, String marketId, String question, String outcome,
                   BigDecimal payout) {
        Pending withQuestion(String q) {
            return new Pending(id, kind, conditionId, marketId, q, outcome, payout);
        }
    }

    /** How to split {@code pending} results given {@code slots} messages left today. */
    record Plan(int single, boolean digest) {}

    static Plan plan(int pending, int slots) {
        if (slots <= 0 || pending == 0) {
            return new Plan(0, false);
        }
        if (pending <= slots) {
            return new Plan(pending, false);
        }
        return new Plan(slots - 1, true);
    }

    public int sendPending() {
        List<Long> users = jdbc.queryForList(
                "SELECT DISTINCT user_id FROM result_notification WHERE status = 'PENDING'", Long.class);
        if (users.isEmpty()) {
            return 0;
        }
        String botName = bot.username().block(Duration.ofSeconds(10));
        int sent = 0;
        for (long userId : users) {
            try {
                sent += sendTo(userId, botName);
            } catch (RuntimeException e) {
                log.warn("Result messages for user {} failed: {}", userId, e.toString());
            }
        }
        if (sent > 0) {
            log.info("Sent {} result messages", sent);
        }
        return sent;
    }

    private int sendTo(long userId, String botName) {
        Map<String, Object> user = jdbc.queryForMap(
                "SELECT telegram_id, allows_messages, language_code FROM app_user WHERE id = ?", userId);
        if (!Boolean.TRUE.equals(user.get("allows_messages"))) {
            jdbc.update("UPDATE result_notification SET status = 'SKIPPED' WHERE user_id = ? AND status = 'PENDING'", userId);
            return 0;
        }
        Integer used = jdbc.queryForObject("""
                SELECT COUNT(*) FILTER (WHERE status = 'SENT')
                     + COUNT(DISTINCT sent_at) FILTER (WHERE status = 'DIGEST')
                FROM result_notification WHERE user_id = ? AND sent_at > NOW() - INTERVAL '24 hours'
                """, Integer.class, userId);
        List<Pending> pending = jdbc.query("""
                SELECT id, kind, condition_id, market_id, question, outcome_label, payout_usdc
                FROM result_notification WHERE user_id = ? AND status = 'PENDING' ORDER BY created_at, id
                """, (rs, i) -> new Pending(rs.getLong("id"), rs.getString("kind"), rs.getString("condition_id"),
                rs.getString("market_id"), rs.getString("question"), rs.getString("outcome_label"),
                rs.getBigDecimal("payout_usdc")), userId);
        Plan plan = plan(pending.size(), DAILY_LIMIT - (used == null ? 0 : used));
        if (plan.single() == 0 && !plan.digest()) {
            return 0;
        }
        String locale = MarketLocale.fromHeader((String) user.get("language_code"));
        NotificationTexts.Texts t = NotificationTexts.of(locale);
        List<Pending> localized = translations.translate(pending, locale, Pending::conditionId, Pending::withQuestion)
                .block(Duration.ofSeconds(10));
        if (localized == null) {
            localized = pending;
        }
        long chatId = ((Number) user.get("telegram_id")).longValue();
        int sent = 0;
        try {
            for (Pending p : localized.subList(0, plan.single())) {
                Button b = button(p, t, botName);
                send(chatId, single(p, t, accuracy(userId, t)), b);
                jdbc.update("UPDATE result_notification SET status = 'SENT', sent_at = NOW() WHERE id = ?", p.id());
                sent++;
            }
            if (plan.digest()) {
                List<Pending> rest = localized.subList(plan.single(), localized.size());
                send(chatId, digest(rest, t), new Button(t.openApp(), link(botName, "snotify")));
                java.sql.Timestamp now = new java.sql.Timestamp(System.currentTimeMillis());
                for (Pending p : rest) {
                    jdbc.update("UPDATE result_notification SET status = 'DIGEST', sent_at = ? WHERE id = ?", now, p.id());
                }
                sent++;
            }
        } catch (ApiException e) {
            if (TelegramBotClient.FORBIDDEN.equals(e.getCode())) {
                log.info("User {} blocked the bot; result messages off", userId);
                jdbc.update("UPDATE app_user SET allows_messages = FALSE WHERE id = ?", userId);
                jdbc.update("UPDATE result_notification SET status = 'SKIPPED' WHERE user_id = ? AND status = 'PENDING'",
                        userId);
            } else {
                throw e;
            }
        }
        return sent;
    }

    private void send(long chatId, String html, Button button) {
        bot.sendMessage(chatId, html, button.text(), button.url()).block(Duration.ofSeconds(15));
        try {
            Thread.sleep(PAUSE_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    record Button(String text, String url) {}

    private static Button button(Pending p, NotificationTexts.Texts t, String botName) {
        return switch (p.kind()) {
            case "BET_WON" -> new Button(t.claim(), link(botName, "w_snotify"));
            case "PREDICTION_WRONG" -> new Button(t.moreMarkets(), link(botName, "snotify"));
            default -> p.marketId() != null && p.marketId().matches("\\d+")
                    ? new Button(t.openMarket(), link(botName, "m_" + p.marketId() + "_snotify"))
                    : new Button(t.openApp(), link(botName, "snotify"));
        };
    }

    private static String link(String botName, String startParam) {
        return "https://t.me/" + botName + "?startapp=" + startParam;
    }

    private String accuracy(long userId, NotificationTexts.Texts t) {
        Map<String, Object> c = jdbc.queryForMap("SELECT COUNT(*) FILTER (WHERE correct) AS ok, "
                + "COUNT(*) FILTER (WHERE resolved_at IS NOT NULL) AS total FROM prediction WHERE user_id = ?", userId);
        long total = ((Number) c.get("total")).longValue();
        return total == 0 ? "" : t.accuracy().replace("{c}", String.valueOf(c.get("ok")))
                .replace("{t}", String.valueOf(total));
    }

    static String single(Pending p, NotificationTexts.Texts t, String accuracy) {
        String template = switch (p.kind()) {
            case "PREDICTION_CORRECT" -> t.predCorrect();
            case "PREDICTION_WRONG" -> t.predWrong();
            default -> t.betWon();
        };
        String text = fill(template, p, t);
        return p.kind().startsWith("PREDICTION") && !accuracy.isEmpty() ? text + "\n" + accuracy : text;
    }

    static String digest(List<Pending> items, NotificationTexts.Texts t) {
        StringBuilder sb = new StringBuilder(t.digestTitle());
        for (Pending p : items) {
            String line = switch (p.kind()) {
                case "PREDICTION_CORRECT" -> t.digestCorrect();
                case "PREDICTION_WRONG" -> t.digestWrong();
                default -> t.digestWon();
            };
            sb.append("\n").append(fill(line, p, t));
        }
        return sb.toString();
    }

    private static String fill(String template, Pending p, NotificationTexts.Texts t) {
        String outcome = "yes".equalsIgnoreCase(p.outcome()) ? t.yes() : "no".equalsIgnoreCase(p.outcome()) ? t.no()
                : p.outcome();
        String payout = p.payout() == null ? "0" : p.payout().setScale(2, RoundingMode.HALF_UP).toPlainString();
        return template.replace("{q}", ShareService.escape(p.question()))
                .replace("{o}", ShareService.escape(outcome))
                .replace("{p}", payout);
    }
}
