package com.polymarket.tma.trading.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
            Long expiration                          // unix seconds, optional
    ) {}

    /** EIP-712 typed data the client must sign with its Privy wallet. */
    public record TypedDataResponse(
            String orderHash,
            Map<String, Object> typedData
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
