package com.polymarket.tma.trading.relayer;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

public final class DepositWalletDtos {

    private DepositWalletDtos() {}

    /**
     * {@code approved} / {@code collateralBalance} are {@code null} when the Polygon RPC is
     * unavailable — the UI should then treat the state as unknown rather than missing.
     */
    public record ApprovalState(
            DepositWalletApprovals.Kind kind,
            String token,
            String spender,
            Boolean approved) {}

    public record Status(
            String owner,
            String address,
            boolean deployed,
            /** pUSD balance in micro units (6 decimals), decimal string. */
            String collateralBalance,
            List<ApprovalState> approvals,
            /** Deployed and every approval confirmed on-chain. */
            boolean readyToTrade) {}

    public record RelayerTx(
            String transactionId,
            String state,
            String transactionHash) {}

    /** EIP-712 {@code Batch} for the owner to sign; {@code batchHash} identifies it on submit. */
    public record PreparedBatch(
            String batchHash,
            Map<String, Object> typedData,
            int callCount) {}

    public record SubmitBatchRequest(
            @NotBlank String batchHash,
            @NotBlank String signature) {}
}
