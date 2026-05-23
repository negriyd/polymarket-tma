package com.polymarket.tma.trading.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.polymarket.tma.wallet.ApprovalDtos;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.Map;

public final class OrderDtos {

    private OrderDtos() {}

    public enum Side { BUY, SELL }
    public enum OrderType { GTC, FOK, GTD }
    public enum SignatureType { EOA, POLY_PROXY, POLY_GNOSIS_SAFE }

    public record PrepareOrderRequest(
            @NotBlank String conditionId,
            @NotBlank String tokenId,
            @NotNull Side side,
            @NotNull @Positive BigDecimal price,    // 0..1
            @NotNull @Positive BigDecimal size,     // outcome shares
            OrderType orderType,
            SignatureType signatureType,
            Long expiration,                         // unix seconds, optional
            /** Optional. Required when {@link #signatureType} is not {@link SignatureType#EOA}. */
            String makerAddress
    ) {}

    /**
     * EIP-712 typed data the client must sign with its Privy wallet.
     *
     * <p>{@link #feeTx} is non-null when {@code app.fees.spread-bps > 0} and a recipient is
     * configured: the wallet must broadcast it (a USDC ERC-20 transfer) right before submitting
     * the signed order. {@link #feeAmountUsdc} is the human-readable fee in USDC for UI display.
     */
    public record TypedDataResponse(
            String orderHash,
            Map<String, Object> typedData,
            ApprovalDtos.UnsignedTx feeTx,
            String feeAmountUsdc,
            Integer feeBps
    ) {}

    public record SubmitOrderRequest(
            @NotBlank String orderHash,
            @NotBlank String signature,
            @JsonProperty("idempotency_key") String idempotencyKey
    ) {}

    public record SubmittedOrderResponse(
            String orderId,
            String status,
            String txHash
    ) {}
}
