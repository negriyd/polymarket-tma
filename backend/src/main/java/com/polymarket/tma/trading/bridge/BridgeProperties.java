package com.polymarket.tma.trading.bridge;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Polymarket Bridge ({@code bridge.polymarket.com}): per-wallet deposit addresses on other chains
 * whose funds are swapped to pUSD and credited to the deposit wallet on Polygon.
 *
 * <p>{@code builderCode} (bytes32 from polymarket.com → Settings → Builders) is optional and public.
 * It attributes bridge traffic (so stuck transfers can be traced) and is signed into every CLOB order
 * ({@code OrderBuilder}) so Polymarket's Builder Program credits the volume to this app.
 */
@ConfigurationProperties(prefix = "app.bridge")
public record BridgeProperties(
        String baseUrl,
        String builderCode
) {}
