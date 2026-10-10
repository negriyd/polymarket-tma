package com.polymarket.tma.trading.bridge;

import java.util.List;

public final class FundingDtos {

    private FundingDtos() {}

    /** One way to top up: send {@code symbol} on {@code chainName} to {@code depositAddress}. */
    public record Option(
            String chainId,
            String chainName,
            String symbol,
            String tokenAddress,
            Integer decimals,
            Double minUsd,
            String depositAddress) {}

    public record Funding(
            /** Polygon deposit wallet credited with pUSD. */
            String depositWallet,
            BridgeClient.DepositAddresses bridgeAddresses,
            List<Option> options) {}

    public record Deposit(
            String fromChainId,
            String fromTokenAddress,
            String fromAmountBaseUnit,
            String status,
            String txHash,
            Long createdTimeMs) {}
}
