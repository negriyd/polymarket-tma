package com.polymarket.tma.trading.bridge;

import com.polymarket.tma.trading.relayer.DepositWalletDtos;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;

public final class WithdrawDtos {

    private WithdrawDtos() {}

    /**
     * {@code DIRECT}: plain pUSD transfer on Polygon (no bridge, no Uniswap liquidity needed).
     * {@code BRIDGE}: pUSD is unwrapped and swapped by the Polymarket bridge into {@code symbol} on {@code chainName}.
     */
    public enum Route { DIRECT, BRIDGE }

    public record Destination(
            Route route,
            String chainId,
            String chainName,
            String symbol,
            String tokenAddress,
            Integer decimals,
            Double minUsd) {}

    public record Options(
            String depositWallet,
            /** pUSD available, micro units; null when the RPC is unavailable. */
            String collateralBalance,
            List<Destination> destinations) {}

    public record PrepareRequest(
            @NotBlank String toChainId,
            @NotBlank String toTokenAddress,
            @NotBlank String recipient,
            /** pUSD to withdraw in micro units (6 decimals). */
            @NotBlank @Pattern(regexp = "^[0-9]{1,30}$") String amountBaseUnit) {}

    /**
     * Batch moving pUSD out of the deposit wallet; sign and submit via
     * {@code /api/wallet/deposit-wallet/batches/submit}. {@code estReceivedBaseUnit} is the bridge's
     * estimate in the destination token's units (null for DIRECT or when the quote failed).
     */
    public record Prepared(
            DepositWalletDtos.PreparedBatch batch,
            Route route,
            String sendTo,
            String estReceivedBaseUnit,
            Long estDurationMs) {}
}
