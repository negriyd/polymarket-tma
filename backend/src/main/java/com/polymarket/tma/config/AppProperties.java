package com.polymarket.tma.config;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Cors cors,
        Jwt jwt,
        Telegram telegram,
        Polymarket polymarket,
        Polygon polygon,
        Privy privy
) {
    public record Cors(List<String> allowedOrigins) {}

    public record Jwt(
            @NotBlank String secret,
            Duration accessTtl,
            Duration refreshTtl,
            String issuer
    ) {}

    public record Telegram(
            String botToken,
            Duration initDataTtl
    ) {}

    public record Polymarket(
            String gammaBaseUrl,
            String clobBaseUrl,
            String dataBaseUrl,
            String wsUrl,
            Duration listCacheTtl,
            Duration detailCacheTtl,
            Duration orderbookCacheTtl,
            Duration historyCacheTtl
    ) {}

    public record Polygon(
            String rpcUrl,
            String usdcAddress,
            String ctfExchangeAddress,
            String ctfAddress
    ) {}

    public record Privy(String appId, String appSecret) {}
}
