package com.polymarket.tma.auth.telegram;

import java.time.Instant;

/** Parsed and validated Telegram WebApp initData. */
public record TelegramInitData(
        TelegramUser user,
        Instant authDate,
        String queryId,
        String startParam,
        String chatType,
        String chatInstance,
        String hash
) {
}
