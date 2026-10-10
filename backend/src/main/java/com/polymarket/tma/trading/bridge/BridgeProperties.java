package com.polymarket.tma.trading.bridge;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Polymarket Bridge ({@code bridge.polymarket.com}): per-wallet deposit addresses on other chains
 * whose funds are swapped to pUSD and credited to the deposit wallet on Polygon.
 *
 * <p>{@code builderCode} (bytes32 from polymarket.com → Settings → Builders) is optional; it only
 * attributes traffic so stuck transfers can be traced. Public, not a secret.
 */
@ConfigurationProperties(prefix = "app.bridge")
public record BridgeProperties(
        String baseUrl,
        String builderCode
) {}
