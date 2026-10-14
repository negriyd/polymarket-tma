package com.polymarket.tma.share;

import com.fasterxml.jackson.databind.JsonNode;
import com.polymarket.tma.config.AppProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Telegram bot updates ({@code POST /api/telegram/webhook}). Only inline queries are subscribed.
 * Telegram proves itself with the secret token set in {@code setWebhook}.
 */
@RestController
public class TelegramWebhookController {

    private static final Logger log = LoggerFactory.getLogger(TelegramWebhookController.class);

    private final InlineQueryService inline;
    private final TelegramBotClient bot;
    private final AppProperties props;
    private final com.polymarket.tma.groups.GroupService groups;

    public TelegramWebhookController(InlineQueryService inline, TelegramBotClient bot, AppProperties props,
                                     com.polymarket.tma.groups.GroupService groups) {
        this.groups = groups;
        this.inline = inline;
        this.bot = bot;
        this.props = props;
    }

    @PostMapping("/api/telegram/webhook")
    public ResponseEntity<Void> update(
            @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String secret,
            @RequestBody JsonNode update) {
        String expected = props.telegram().webhookSecret();
        if (expected == null || expected.isBlank() || secret == null || !MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), secret.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(401).build();
        }
        if (update.has("inline_query")) {
            // Answer asynchronously; Telegram only needs a quick 200 for the update itself.
            inline.answer(update.get("inline_query")).subscribe();
        } else if (update.has("message") || update.has("my_chat_member")) {
            try {
                groups.handle(update);
            } catch (RuntimeException e) {
                // Always 200: Telegram would otherwise redeliver the update again and again.
                log.warn("Telegram update {} failed: {}", update.path("update_id").asText(), e.toString());
            }
        }
        return ResponseEntity.ok().build();
    }

    /** Registers the webhook on startup when it's configured (idempotent on Telegram's side). */
    @EventListener(ApplicationReadyEvent.class)
    public void registerWebhook() {
        String url = props.telegram().webhookUrl();
        String secret = props.telegram().webhookSecret();
        if (url == null || url.isBlank() || secret == null || secret.isBlank()) {
            log.info("Telegram webhook not configured; inline mode is off");
            return;
        }
        bot.setWebhook(url, secret).subscribe(
                ok -> { },
                e -> log.warn("Telegram setWebhook failed: {}", e.toString()),
                () -> log.info("Telegram webhook registered at {}", url));
        bot.setGroupCommands().subscribe(ok -> { }, e -> log.warn("Telegram setMyCommands failed: {}", e.toString()));
    }
}
