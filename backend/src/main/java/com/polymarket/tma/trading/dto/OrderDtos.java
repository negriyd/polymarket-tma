package com.polymarket.tma.trading.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.polymarket.tma.trading.relayer.DepositWalletDtos;
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
    /**
     * CLOB v2 order signature types. {@link #POLY_1271} is the Deposit Wallet flow (mandatory for
     * wallets onboarded after 2026-05): maker = signer = deposit wallet, the EOA signs an
     * ERC-7739 {@code TypedDataSign} wrapper and the backend packs the final ERC-1271 signature.
     */
    public enum SignatureType { EOA, POLY_PROXY, POLY_GNOSIS_SAFE, POLY_1271 }

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
     * <p>When {@code app.fees.spread-bps > 0} and a recipient is configured, the platform fee comes
     * as either {@link #feeTx} (EOA orders: a USDC transfer the wallet broadcasts) or
     * {@link #feeBatch} ({@code POLY_1271}: a pUSD transfer from the deposit wallet the owner signs
     * and submits via {@code /api/wallet/deposit-wallet/batches/submit}). Never both.
     * {@link #feeAmountUsdc} is the human-readable fee for UI display.
     */
    public record TypedDataResponse(
            String orderHash,
            Map<String, Object> typedData,
            ApprovalDtos.UnsignedTx feeTx,
            DepositWalletDtos.PreparedBatch feeBatch,
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
