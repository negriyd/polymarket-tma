package com.polymarket.tma.share;

import com.fasterxml.jackson.databind.JsonNode;
import com.polymarket.tma.common.ApiException;
import com.polymarket.tma.config.AppProperties;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/** Minimal Telegram Bot API client for sharing: the bot's username and prepared inline messages. */
@Component
public class TelegramBotClient {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(8);

    private final WebClient client;
    private final boolean configured;
    private Mono<String> username;

    public TelegramBotClient(WebClient.Builder builder, AppProperties props) {
        String token = props.telegram().botToken();
        this.configured = token != null && !token.isBlank();
        this.client = builder.baseUrl("https://api.telegram.org/bot" + (configured ? token : "missing")).build();
        this.username = Mono.defer(this::fetchUsername).cache(
                name -> Duration.ofDays(1), err -> Duration.ZERO, () -> Duration.ZERO);
    }

    /** The bot's @username (without @), for t.me links. Cached for a day. */
    public Mono<String> username() {
        return username;
    }

    private Mono<String> fetchUsername() {
        if (!configured) {
            return Mono.error(ApiException.internal("BOT_NOT_CONFIGURED", "BOT_TOKEN is not set"));
        }
        return call("getMe", Map.of()).map(result -> result.path("username").asText(""))
                .filter(name -> !name.isBlank())
                .switchIfEmpty(Mono.error(ApiException.upstream("TELEGRAM_GETME", "getMe returned no username")));
    }

    /**
     * Stores a message the user can send to any chat via {@code WebApp.shareMessage(id)} (Bot API 8.0).
     * Returns the prepared message id.
     */
    public Mono<String> savePreparedInlineMessage(long telegramUserId, Map<String, Object> result) {
        Map<String, Object> body = Map.of(
                "user_id", telegramUserId,
                "result", result,
                "allow_user_chats", true,
                "allow_bot_chats", false,
                "allow_group_chats", true,
                "allow_channel_chats", true);
        return call("savePreparedInlineMessage", body).map(r -> r.path("id").asText(""))
                .filter(id -> !id.isBlank())
                .switchIfEmpty(Mono.error(ApiException.upstream("TELEGRAM_PREPARE", "No prepared message id")));
    }

    /** The user blocked the bot or never allowed it to write: stop messaging them. */
    public static final String FORBIDDEN = "TELEGRAM_FORBIDDEN";

    /**
     * HTML message with one URL button. Fails with {@link #FORBIDDEN} when Telegram answers 403.
     */
    public Mono<Void> sendMessage(long chatId, String html, String buttonText, String buttonUrl) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("chat_id", chatId);
        body.put("text", html);
        body.put("parse_mode", "HTML");
        body.put("link_preview_options", Map.of("is_disabled", true));
        if (buttonText != null && buttonUrl != null) {
            body.put("reply_markup", Map.of("inline_keyboard",
                    java.util.List.of(java.util.List.of(Map.of("text", buttonText, "url", buttonUrl)))));
        }
        return client.post().uri("/sendMessage").bodyValue(body)
                .exchangeToMono(resp -> resp.bodyToMono(JsonNode.class)
                        .map(json -> Map.entry(resp.statusCode().value(), json)))
                .timeout(TIMEOUT)
                .flatMap(reply -> {
                    JsonNode json = reply.getValue();
                    if (json.path("ok").asBoolean(false)) {
                        return Mono.<Void>empty();
                    }
                    int status = json.path("error_code").asInt(reply.getKey());
                    String desc = json.path("description").asText("?");
                    return Mono.<Void>error(status == 403
                            ? ApiException.forbidden(FORBIDDEN, desc)
                            : ApiException.upstream("TELEGRAM_SENDMESSAGE", "sendMessage failed: " + desc));
                });
    }

    /** Answers an inline query ({@code @bot text} in any chat) with market cards. */
    public Mono<Void> answerInlineQuery(String queryId, java.util.List<Map<String, Object>> results) {
        return call("answerInlineQuery", Map.of(
                "inline_query_id", queryId,
                "results", results,
                "cache_time", 30,
                // Results carry the asking user's invite code, so they must not be shared across users.
                "is_personal", true)).then();
    }

    /** Points Telegram at our webhook: inline queries, commands, and being added to / removed from groups. */
    public Mono<Void> setWebhook(String url, String secret) {
        return call("setWebhook", Map.of(
                "url", url,
                "secret_token", secret,
                "allowed_updates", java.util.List.of("inline_query", "message", "my_chat_member"))).then();
    }

    /** The command menu in group chats. */
    public Mono<Void> setGroupCommands() {
        return call("setMyCommands", Map.of(
                "scope", Map.of("type", "all_group_chats"),
                "commands", java.util.List.of(
                        Map.of("command", "top", "description", "Top forecasters of this chat"),
                        Map.of("command", "join", "description", "Join this chat's leaderboard")))).then();
    }

    private Mono<JsonNode> call(String method, Map<String, Object> body) {
        return client.post().uri("/" + method).bodyValue(body)
                .exchangeToMono(resp -> resp.bodyToMono(JsonNode.class))
                .timeout(TIMEOUT)
                .flatMap(json -> json.path("ok").asBoolean(false)
                        ? Mono.just(json.path("result"))
                        : Mono.error(ApiException.upstream("TELEGRAM_" + method.toUpperCase(),
                                "Telegram " + method + " failed: " + json.path("description").asText("?"))))
                .doOnError(e -> log.warn("Telegram {} failed: {}", method, e.toString()));
    }
}
