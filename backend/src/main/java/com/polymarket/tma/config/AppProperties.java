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
        Privy privy,
        Fees fees
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
            /**
             * Polymarket CTF Exchange <strong>v2</strong> for binary Yes/No markets. CLOB
             * migrated to v2 on 2026-04-22 — the v1 contract
             * ({@code 0x4bFb41d5B3570DeFd03C39a9A4D8dE6Bd8B8982E}) now returns
             * {@code 400 "order_version_mismatch"} for new orders. Used as the EIP-712
             * {@code verifyingContract} when {@code MarketDto.negRisk} is {@code false}/null
             * <em>and</em> as the spender for USDC + CTF approvals.
             */
            String ctfExchangeAddress,
            /**
             * Polymarket NegRisk CTF Exchange <strong>v2</strong> for multi-outcome neg-risk
             * markets. EIP-712 {@code verifyingContract} when {@code MarketDto.negRisk == true}.
             * Sending an order signed against the wrong contract returns
             * {@code 400 "order_version_mismatch"}.
             */
            String negRiskCtfExchangeAddress,
            String ctfAddress
    ) {}

    public record Privy(String appId, String appSecret) {}

    /**
     * Platform monetization fee charged on top of every prepared order.
     *
     * <p>{@code spreadBps} is the fee rate in basis points (100 bps = 1%, 50 bps = 0.5%) applied to
     * the order's USDC notional. The fee is collected as a separate ERC-20 USDC transfer to
     * {@code recipientAddress}, broadcast by the user's wallet right before the order is submitted.
     * Setting either field to {@code null}/blank or {@code spreadBps=0} disables the fee.
     */
    public record Fees(
            Integer spreadBps,
            String recipientAddress
    ) {
        public boolean enabled() {
            return spreadBps != null
                    && spreadBps > 0
                    && recipientAddress != null
                    && !recipientAddress.isBlank();
        }
    }
}
