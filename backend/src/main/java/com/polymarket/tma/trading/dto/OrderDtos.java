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
    /** {@link #FAK}: fill what the book has up to the price cap right away, cancel the rest (one-tap bets). */
    public enum OrderType { GTC, FOK, GTD, FAK }
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
            String makerAddress,
            /** pUSD to spend; required for {@link OrderType#FAK} buys, where {@link #price} is the worst price. */
            BigDecimal amount
    ) {
        public PrepareOrderRequest(String conditionId, String tokenId, Side side, BigDecimal price, BigDecimal size,
                                   OrderType orderType, SignatureType signatureType, Long expiration,
                                   String makerAddress) {
            this(conditionId, tokenId, side, price, size, orderType, signatureType, expiration, makerAddress, null);
        }

        public boolean fakBuy() {
            return orderType == OrderType.FAK && side == Side.BUY;
        }
    }

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

    /**
     * @param filledUsdc    pUSD actually spent, when CLOB reports it (FAK buys)
     * @param feeBatch      for FAK buys the fee is charged on the filled amount, so it is prepared here
     *                      instead of at {@code /prepare}; null when nothing filled or no fee
     */
    public record SubmittedOrderResponse(
            String orderId,
            String status,
            String txHash,
            String filledUsdc,
            DepositWalletDtos.PreparedBatch feeBatch,
            String feeAmountUsdc
    ) {
        public SubmittedOrderResponse(String orderId, String status, String txHash) {
            this(orderId, status, txHash, null, null, null);
        }

        public SubmittedOrderResponse withFee(DepositWalletDtos.PreparedBatch batch, String amountUsdc) {
            return new SubmittedOrderResponse(orderId, status, txHash, filledUsdc, batch, amountUsdc);
        }
    }
}
