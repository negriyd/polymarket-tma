package com.polymarket.tma.auth.dto;

import jakarta.validation.constraints.NotBlank;

public final class AuthDtos {

    private AuthDtos() {}

    public record TelegramLoginRequest(@NotBlank String initData) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    public record TokenPair(
            String accessToken,
            String refreshToken,
            long expiresInSec,
            UserInfo user
    ) {}

    public record UserInfo(
            long id,
            long telegramId,
            String username,
            String firstName,
            String lastName,
            String photoUrl,
            String languageCode,
            boolean premium,
            String walletAddress
    ) {}
}
