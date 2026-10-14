package com.polymarket.tma.groups;

import com.fasterxml.jackson.databind.JsonNode;
import com.polymarket.tma.market.i18n.MarketLocale;
import com.polymarket.tma.share.ShareService;
import com.polymarket.tma.share.TelegramBotClient;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Forecasting leaderboards in group chats: the bot greets a group it's added to with a join link,
 * answers /top and /join, and posts last week's winners every Monday. Members are app users who
 * joined via /join or the group's app link; the board ranks their free predictions resolved in the
 * last 7 days by how many were right.
 */
@Service
public class GroupService {

    private static final Logger log = LoggerFactory.getLogger(GroupService.class);
    static final int TOP = 10;
    /** A weekly post needs a race. */
    static final int MIN_MEMBERS_FOR_WEEKLY = 2;

    private final JdbcTemplate jdbc;
    private final TelegramBotClient bot;

    public GroupService(JdbcTemplate jdbc, TelegramBotClient bot) {
        this.jdbc = jdbc;
        this.bot = bot;
    }

    record Row(String name, long correct, long total) {}

    /** A command sent to us: {@code /top@ourbot} or plain {@code /top}; null when it's for another bot. */
    static String command(String text, String botName) {
        if (text == null || !text.startsWith("/")) {
            return null;
        }
        String first = text.split("\\s+", 2)[0].substring(1);
        int at = first.indexOf('@');
        if (at >= 0) {
            if (botName == null || !first.substring(at + 1).equalsIgnoreCase(botName)) {
                return null;
            }
            first = first.substring(0, at);
        }
        return first.toLowerCase(Locale.ROOT);
    }

    static boolean isGroup(JsonNode chat) {
        String type = chat.path("type").asText();
        return type.equals("group") || type.equals("supergroup");
    }

    /** Telegram update from the webhook: bot added/removed, a command, or a group upgraded to a supergroup. */
    public void handle(JsonNode update) {
        String botName = bot.username().block(Duration.ofSeconds(10));
        if (update.has("my_chat_member")) {
            onMembership(update.get("my_chat_member"), botName);
            return;
        }
        JsonNode msg = update.path("message");
        if (msg.isMissingNode()) {
            return;
        }
        JsonNode chat = msg.path("chat");
        long chatId = chat.path("id").asLong();
        if (msg.has("migrate_to_chat_id")) {
            jdbc.update("UPDATE group_chat SET chat_id = ? WHERE chat_id = ?", msg.get("migrate_to_chat_id").asLong(), chatId);
            return;
        }
        String cmd = command(msg.path("text").asText(null), botName);
        if (cmd == null) {
            return;
        }
        JsonNode from = msg.path("from");
        String lang = MarketLocale.fromHeader(from.path("language_code").asText(null));
        GroupTexts.Texts t = GroupTexts.of(lang);
        if (!isGroup(chat)) {
            if (cmd.equals("start")) {
                send(chatId, t.startPrivate(), t.openApp(), link(botName, "sbot"));
            }
            return;
        }
        ensureGroup(chatId, chat.path("title").asText(null), from.path("language_code").asText(null));
        switch (cmd) {
            case "top" -> send(chatId, board(t, t.topTitle(), leaderboard(chatId)), t.joinButton(), joinLink(botName, chatId));
            case "join" -> join(chatId, from, t, botName);
            case "start", "help" -> send(chatId, t.welcome(), t.joinButton(), joinLink(botName, chatId));
            default -> { }
        }
    }

    private void onMembership(JsonNode m, String botName) {
        JsonNode chat = m.path("chat");
        if (!isGroup(chat)) {
            return;
        }
        long chatId = chat.path("id").asLong();
        String status = m.path("new_chat_member").path("status").asText();
        if (status.equals("member") || status.equals("administrator")) {
            String lang = m.path("from").path("language_code").asText(null);
            jdbc.update("""
                    INSERT INTO group_chat (chat_id, title, language_code) VALUES (?, ?, ?)
                    ON CONFLICT (chat_id) DO UPDATE SET active = TRUE, title = EXCLUDED.title,
                        language_code = EXCLUDED.language_code
                    """, chatId, chat.path("title").asText(null), lang);
            GroupTexts.Texts t = GroupTexts.of(MarketLocale.fromHeader(lang));
            send(chatId, t.welcome(), t.joinButton(), joinLink(botName, chatId));
            log.info("Added to group {}", chatId);
        } else if (status.equals("left") || status.equals("kicked")) {
            jdbc.update("UPDATE group_chat SET active = FALSE WHERE chat_id = ?", chatId);
            log.info("Removed from group {}", chatId);
        }
    }

    private void ensureGroup(long chatId, String title, String lang) {
        jdbc.update("""
                INSERT INTO group_chat (chat_id, title, language_code) VALUES (?, ?, ?)
                ON CONFLICT (chat_id) DO UPDATE SET active = TRUE, title = COALESCE(EXCLUDED.title, group_chat.title)
                """, chatId, title, lang);
    }

    private void join(long chatId, JsonNode from, GroupTexts.Texts t, String botName) {
        String name = ShareService.escape(displayName(from.path("first_name").asText(null),
                from.path("username").asText(null)));
        List<Long> ids = jdbc.queryForList("SELECT id FROM app_user WHERE telegram_id = ?", Long.class,
                from.path("id").asLong());
        if (ids.isEmpty()) {
            send(chatId, t.needApp().replace("{name}", name), t.joinButton(), joinLink(botName, chatId));
            return;
        }
        addMember(chatId, ids.get(0));
        send(chatId, t.joined().replace("{name}", name), t.joinButton(), joinLink(botName, chatId));
    }

    private boolean addMember(long chatId, long userId) {
        return jdbc.update("""
                INSERT INTO group_member (chat_id, user_id)
                SELECT ?, ? WHERE EXISTS (SELECT 1 FROM group_chat WHERE chat_id = ? AND active)
                ON CONFLICT DO NOTHING
                """, chatId, userId, chatId) > 0;
    }

    /** Someone opened the app through a group's link: join that group's board, and say so there once. */
    public void joinFromApp(long chatId, long userId, String firstName, String username) {
        try {
            if (!addMember(chatId, userId)) {
                return;
            }
            String lang = jdbc.queryForObject("SELECT language_code FROM group_chat WHERE chat_id = ?", String.class, chatId);
            GroupTexts.Texts t = GroupTexts.of(MarketLocale.fromHeader(lang));
            String botName = bot.username().block(Duration.ofSeconds(10));
            send(chatId, t.joined().replace("{name}", ShareService.escape(displayName(firstName, username))),
                    t.joinButton(), joinLink(botName, chatId));
        } catch (RuntimeException e) {
            log.warn("Joining group {} from the app failed: {}", chatId, e.toString());
        }
    }

    List<Row> leaderboard(long chatId) {
        return jdbc.query("""
                SELECT u.first_name, u.username,
                       COUNT(*) FILTER (WHERE p.correct) AS ok, COUNT(*) AS total
                FROM group_member g
                JOIN app_user u ON u.id = g.user_id
                JOIN prediction p ON p.user_id = g.user_id AND p.resolved_at > NOW() - INTERVAL '7 days'
                WHERE g.chat_id = ?
                GROUP BY u.id, u.first_name, u.username
                ORDER BY ok DESC, total ASC, u.id
                LIMIT ?
                """, (rs, i) -> new Row(displayName(rs.getString("first_name"), rs.getString("username")),
                rs.getLong("ok"), rs.getLong("total")), chatId, TOP);
    }

    static String board(GroupTexts.Texts t, String title, List<Row> rows) {
        if (rows.isEmpty()) {
            return title + "\n\n" + t.topEmpty();
        }
        StringBuilder sb = new StringBuilder(title).append("\n");
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            String place = switch (i) {
                case 0 -> "🥇";
                case 1 -> "🥈";
                case 2 -> "🥉";
                default -> String.valueOf(i + 1);
            };
            sb.append("\n").append(t.topLine().replace("{i}.", i < 3 ? place : place + ".")
                    .replace("{name}", ShareService.escape(r.name()))
                    .replace("{c}", String.valueOf(r.correct())).replace("{t}", String.valueOf(r.total())));
        }
        return sb.toString();
    }

    /** Monday: last week's winners in every active group with a race; once per group per week. */
    public int weekly() {
        String ref = com.polymarket.tma.watch.WeeklyDigestService.weekRef(LocalDate.now(ZoneOffset.UTC));
        List<Map<String, Object>> groups = jdbc.queryForList("""
                SELECT c.chat_id, c.language_code FROM group_chat c
                WHERE c.active AND (c.last_weekly IS NULL OR c.last_weekly <> ?)
                  AND (SELECT COUNT(*) FROM group_member m WHERE m.chat_id = c.chat_id) >= ?
                """, ref, MIN_MEMBERS_FOR_WEEKLY);
        if (groups.isEmpty()) {
            return 0;
        }
        String botName = bot.username().block(Duration.ofSeconds(10));
        int posted = 0;
        for (Map<String, Object> g : groups) {
            long chatId = ((Number) g.get("chat_id")).longValue();
            try {
                List<Row> rows = leaderboard(chatId);
                if (!rows.isEmpty()) {
                    GroupTexts.Texts t = GroupTexts.of(MarketLocale.fromHeader((String) g.get("language_code")));
                    send(chatId, board(t, t.weeklyTitle(), rows), t.joinButton(), joinLink(botName, chatId));
                    posted++;
                }
                jdbc.update("UPDATE group_chat SET last_weekly = ? WHERE chat_id = ?", ref, chatId);
            } catch (RuntimeException e) {
                log.warn("Weekly post to group {} failed: {}", chatId, e.toString());
            }
        }
        log.info("Posted weekly leaderboards to {} groups ({})", posted, ref);
        return posted;
    }

    private void send(long chatId, String html, String button, String url) {
        try {
            bot.sendMessage(chatId, html, button, url).block(Duration.ofSeconds(15));
        } catch (RuntimeException e) {
            if (e instanceof com.polymarket.tma.common.ApiException a && TelegramBotClient.FORBIDDEN.equals(a.getCode())) {
                // Kicked or can't write there any more.
                jdbc.update("UPDATE group_chat SET active = FALSE WHERE chat_id = ?", chatId);
            }
            throw e;
        }
    }

    static String displayName(String firstName, String username) {
        if (firstName != null && !firstName.isBlank()) {
            return firstName.strip();
        }
        return username != null && !username.isBlank() ? "@" + username : "?";
    }

    static String joinLink(String botName, long chatId) {
        return link(botName, "g" + Math.abs(chatId) + "_sgroup");
    }

    private static String link(String botName, String startParam) {
        return "https://t.me/" + botName + "?startapp=" + startParam;
    }
}
