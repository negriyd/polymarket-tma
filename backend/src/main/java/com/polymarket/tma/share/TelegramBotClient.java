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
