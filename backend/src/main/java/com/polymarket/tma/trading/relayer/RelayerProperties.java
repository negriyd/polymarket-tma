package com.polymarket.tma.trading.relayer;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Polymarket Relayer ({@code relayer-v2.polymarket.com}) — gasless deposit-wallet deployment and
 * batched calls (approvals, transfers) for CLOB v2 {@code POLY_1271} trading.
 *
 * <p>Builder credentials come from polymarket.com → Settings → Builders and stay server-side.
 * {@code depositWalletBeacon} is the factory's {@code beacon()}; wallets deployed since 2026-06-29
 * are beacon proxies. {@code depositWalletImplementation} is only needed to recognise older UUPS
 * wallets.
 *
 * <p>The remaining addresses are the CLOB v2 trading contracts a deposit wallet must approve, on
 * top of {@code app.polygon.ctf-exchange-address} / {@code neg-risk-ctf-exchange-address} /
 * {@code ctf-address}: collateral is pUSD (wrapped USDC), not USDC.e. The CTF collateral adapters
 * redeem resolved CTF positions straight into pUSD.
 */
@ConfigurationProperties(prefix = "app.relayer")
public record RelayerProperties(
        String baseUrl,
        String builderApiKey,
        String builderSecret,
        String builderPassphrase,
        String depositWalletFactory,
        String depositWalletBeacon,
        String depositWalletImplementation,
        String collateralAddress,
        String exchangeV3Address,
        String negRiskAdapterAddress,
        String positionManagerAddress,
        String ctfCollateralAdapterAddress,
        String negRiskCtfCollateralAdapterAddress
) {
    public boolean builderConfigured() {
        return notBlank(builderApiKey) && notBlank(builderSecret) && notBlank(builderPassphrase);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
