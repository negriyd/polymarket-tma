package com.polymarket.tma.bets.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.polymarket.tma.wallet.ApprovalDtos;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class BetDtos {

    private BetDtos() {}

    public record PrepareBetRequest(
            @NotBlank String conditionId,
            /** 0 = first outcome (typically YES), 1 = second (typically NO). */
            @Min(0) @Max(1) int outcomeIndex,
            @NotNull @DecimalMin("0.000001") BigDecimal amountUsdc,
            @JsonProperty("idempotency_key") String idempotencyKey
    ) {}

    public record PrepareBetResponse(
            long betId,
            String conditionId,
            int outcomeIndex,
            String outcomeLabel,
            String amountUsdc,
            int platformFeeBps,
            ApprovalDtos.UnsignedTx paymentTx
    ) {}

    public record ConfirmBetRequest(
            long betId,
            @NotBlank String paymentTxHash,
            @JsonProperty("idempotency_key") String idempotencyKey
    ) {}

    public record BetResponse(
            long id,
            String conditionId,
            int outcomeIndex,
            String outcomeLabel,
            String amountUsdc,
            String status,
            String paymentTxHash,
            Instant createdAt,
            Instant confirmedAt
    ) {}

    public record ConfigResponse(
            boolean enabled,
            int platformFeeBps,
            String minBetUsdc,
            String minWithdrawUsdc,
            int lockBeforeEndMinutes
    ) {}

    public record PoolSide(
            int outcomeIndex,
            String outcomeLabel,
            String totalUsdc,
            String impliedProbability
    ) {}

    public record PoolResponse(
            String conditionId,
            String totalPoolUsdc,
            int platformFeeBps,
            List<PoolSide> sides
    ) {}

    public record BalanceResponse(
            String availableUsdc,
            boolean withdrawEnabled
    ) {}

    public record WithdrawRequest(
            @NotNull @DecimalMin("0.000001") BigDecimal amountUsdc
    ) {}

    public record WithdrawalResponse(
            long id,
            String amountUsdc,
            String status,
            String txHash,
            Instant createdAt,
            Instant sentAt
    ) {}

    public record AdminResolveRequest(
            @Min(0) @Max(1) int winningOutcomeIndex
    ) {}

    public record AdminMarketResponse(
            String conditionId,
            String status,
            Integer winningOutcomeIndex
    ) {}
}
