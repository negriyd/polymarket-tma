package com.polymarket.tma.trading;

import com.polymarket.tma.auth.entity.AppUser;
import com.polymarket.tma.auth.repo.AppUserRepository;
import com.polymarket.tma.common.ApiException;
import com.polymarket.tma.fees.FeeService;
import com.polymarket.tma.fees.FeeService.FeeQuote;
import com.polymarket.tma.market.MarketService;
import com.polymarket.tma.market.dto.MarketDto;
import com.polymarket.tma.trading.OrderBuilder.BuiltOrder;
import com.polymarket.tma.trading.clob.ClobCredentials;
import com.polymarket.tma.trading.clob.ClobCredentialsStore;
import com.polymarket.tma.trading.dto.OrderDtos;
import com.polymarket.tma.trading.entity.OrderAudit;
import com.polymarket.tma.trading.relayer.DepositWalletDtos;
import com.polymarket.tma.trading.relayer.DepositWalletService;
import com.polymarket.tma.trading.repo.OrderAuditRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.math.BigInteger;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

@Service
public class TradingService {

    private static final Logger log = LoggerFactory.getLogger(TradingService.class);

    /** USDC has 6 decimals; used to humanise fee amounts on the wire. */
    private static final BigDecimal DEFAULT_TICK = new BigDecimal("0.01");
    private static final int SIZE_DECIMALS = 2;
    private static final BigDecimal USDC_DECIMALS_FACTOR = new BigDecimal(BigInteger.valueOf(1_000_000L));

    private final OrderBuilder builder;
    private final PendingOrderCache pending;
    private final AppUserRepository userRepo;
    private final OrderAuditRepository auditRepo;
    private final ClobOrderClient clob;
    private final ClobCredentialsStore credentialsStore;
    private final FeeService fees;
    private final MarketService marketService;
    private final DepositWalletService depositWallets;

    public TradingService(OrderBuilder builder,
                          PendingOrderCache pending,
                          AppUserRepository userRepo,
                          OrderAuditRepository auditRepo,
                          ClobOrderClient clob,
                          ClobCredentialsStore credentialsStore,
                          FeeService fees,
                          MarketService marketService,
                          DepositWalletService depositWallets) {
        this.builder = builder;
        this.pending = pending;
        this.userRepo = userRepo;
        this.auditRepo = auditRepo;
        this.clob = clob;
        this.credentialsStore = credentialsStore;
        this.fees = fees;
        this.marketService = marketService;
        this.depositWallets = depositWallets;
    }

    public OrderDtos.TypedDataResponse prepare(long userId, OrderDtos.PrepareOrderRequest req) {
        AppUser user = userRepo.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("USER_GONE", "User not found"));
        if (user.getWalletAddress() == null || user.getWalletAddress().isBlank()) {
            throw ApiException.badRequest("WALLET_REQUIRED",
                    "Wallet address must be set on profile before preparing orders");
        }
        String signer = user.getWalletAddress();
        String maker = resolveMaker(user, req);
        MarketDto market = lookupMarket(req.conditionId());
        boolean negRisk = market != null && Boolean.TRUE.equals(market.negRisk());
        req = fitToOrderRules(req, market);
        BuiltOrder built = builder.build(maker, signer, req, negRisk);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> domain = (java.util.Map<String, Object>) built.typedData().get("domain");
        log.info("Prepared order user={} conditionId={} negRisk={} verifyingContract={}",
                userId, req.conditionId(), negRisk,
                domain == null ? "?" : domain.get("verifyingContract"));
        pending.put(userId, built);
        FeeQuote fee = fees.quote(req);
        if (fee != null && maker.equalsIgnoreCase(fee.recipient())) {
            // The fee recipient trading from its own wallet would only pay itself: skip the extra signature.
            fee = null;
        }
        DepositWalletDtos.PreparedBatch feeBatch = null;
        if (fee != null && req.signatureType() == OrderDtos.SignatureType.POLY_1271) {
            // Funds live in the deposit wallet (pUSD), and the EOA has no gas: charge from there.
            feeBatch = depositWallets.prepareCollateralTransfer(
                    userId, "TRADING_FEE", fee.recipient(), fee.amountMicroUsdc());
        }
        return new OrderDtos.TypedDataResponse(
                built.orderHash(),
                built.typedData(),
                fee == null || feeBatch != null ? null : fee.tx(),
                feeBatch,
                fee == null ? null : new BigDecimal(fee.amountMicroUsdc())
                        .divide(USDC_DECIMALS_FACTOR).toPlainString(),
                fee == null ? null : fee.spreadBps());
    }

    /**
     * Resolves whether {@code conditionId} belongs to a NegRisk multi-outcome market by hitting
     * Gamma (cached). The flag flips the EIP-712 {@code verifyingContract} between the regular
     * CTF Exchange and the NegRisk CTF Exchange — picking the wrong one returns
     * {@code 400 "order_version_mismatch"}.
     *
     * <p>Failure-mode policy: if the lookup fails (Gamma down, market missing), default to
     * {@code false} (regular CTF Exchange). The order will then be rejected by CLOB if the
     * market was actually negRisk, but the user gets a clear error rather than a surprise
     * crash. The cached path is sub-millisecond so blocking here is fine.
     */
    /** The EIP-712 order carries side as uint8 (0 = BUY); the audit stores the name. */
    private static String sideName(Object side) {
        String s = String.valueOf(side);
        return switch (s) {
            case "0" -> "BUY";
            case "1" -> "SELL";
            default -> s;
        };
    }

    private MarketDto lookupMarket(String conditionId) {
        try {
            MarketDto market = marketService.get(conditionId).block(Duration.ofSeconds(3));
            if (market == null) {
                log.warn("MarketService returned null for conditionId={} — defaulting negRisk=false", conditionId);
                return null;
            }
            log.debug("Market {} -> negRisk field = {}", conditionId, market.negRisk());
            return market;
        } catch (RuntimeException e) {
            log.warn("Failed to resolve negRisk for {}: {}", conditionId, e.toString());
            return null;
        }
    }

    /**
     * CLOB derives the price as makerAmount / takerAmount and rejects it unless it lands exactly on
     * the market's tick grid, so the price must already be on the grid and the size is cut to
     * whole cents of a share (2 decimals) — then size × price has at most 6 decimals and the
     * amounts divide back to the exact price. Also enforces the market's minimum order size.
     */
    static OrderDtos.PrepareOrderRequest fitToOrderRules(OrderDtos.PrepareOrderRequest req, MarketDto market) {
        BigDecimal tick = market == null || market.orderPriceMinTickSize() == null
                ? DEFAULT_TICK : market.orderPriceMinTickSize();
        BigDecimal price = req.price();
        if (price.signum() <= 0 || price.compareTo(BigDecimal.ONE) >= 0
                || price.remainder(tick).signum() != 0) {
            throw ApiException.badRequest("PRICE_OFF_TICK",
                    "Price must be between 0 and 1 in steps of " + tick.stripTrailingZeros().toPlainString());
        }
        BigDecimal size = req.size().setScale(SIZE_DECIMALS, RoundingMode.DOWN);
        BigDecimal minSize = market == null ? null : market.orderMinSize();
        if (size.signum() <= 0 || (minSize != null && size.compareTo(minSize) < 0)) {
            BigDecimal min = minSize == null ? BigDecimal.ONE.movePointLeft(SIZE_DECIMALS) : minSize;
            throw ApiException.badRequest("ORDER_TOO_SMALL",
                    "Minimum order is " + min.stripTrailingZeros().toPlainString() + " shares (≈ $"
                            + min.multiply(price).setScale(2, RoundingMode.UP).toPlainString()
                            + " at this price)");
        }
        return new OrderDtos.PrepareOrderRequest(req.conditionId(), req.tokenId(), req.side(), price, size,
                req.orderType(), req.signatureType(), req.expiration(), req.makerAddress());
    }

    /**
     * Resolve the {@code maker} for the EIP-712 order:
     * <ul>
     *     <li>{@link OrderDtos.SignatureType#EOA} (default): always the wallet address (maker == signer).</li>
     *     <li>{@code POLY_1271}: the user's deposit wallet, derived from the wallet (CREATE2). An explicit
     *         {@code makerAddress} is optional but must match.</li>
     *     <li>{@code POLY_PROXY} / {@code POLY_GNOSIS_SAFE}: requires {@code makerAddress} in the request
     *         (the Polymarket proxy / Safe holding the funds). It must differ from the signer.</li>
     * </ul>
     */
    private String resolveMaker(AppUser user, OrderDtos.PrepareOrderRequest req) {
        OrderDtos.SignatureType type = req.signatureType() == null
                ? OrderDtos.SignatureType.EOA
                : req.signatureType();
        if (type == OrderDtos.SignatureType.EOA) {
            return user.getWalletAddress();
        }
        if (type == OrderDtos.SignatureType.POLY_1271) {
            String depositWallet = depositWallets.depositWalletOf(user.getWalletAddress());
            if (req.makerAddress() != null && !req.makerAddress().isBlank()
                    && !req.makerAddress().equalsIgnoreCase(depositWallet)) {
                throw ApiException.badRequest("MAKER_MISMATCH",
                        "makerAddress must be the deposit wallet " + depositWallet + " for signatureType=POLY_1271");
            }
            return depositWallet;
        }
        if (req.makerAddress() == null || req.makerAddress().isBlank()) {
            throw ApiException.badRequest("MAKER_REQUIRED",
                    "makerAddress is required for signatureType=" + type);
        }
        if (req.makerAddress().equalsIgnoreCase(user.getWalletAddress())) {
            throw ApiException.badRequest("MAKER_EQUALS_SIGNER",
                    "makerAddress must differ from the wallet for signatureType=" + type);
        }
        return req.makerAddress();
    }

    public Mono<Void> cancel(long userId, String orderId) {
        AppUser user = userRepo.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("USER_GONE", "User not found"));
        if (user.getWalletAddress() == null || user.getWalletAddress().isBlank()) {
            throw ApiException.badRequest("WALLET_REQUIRED",
                    "Wallet address must be set before cancelling orders");
        }
        ClobCredentials creds = credentialsStore.get(userId);
        return clob.cancel(orderId, user.getWalletAddress(), creds);
    }

    @Transactional
    public Mono<OrderDtos.SubmittedOrderResponse> submit(long userId, OrderDtos.SubmitOrderRequest req) {
        if (req.idempotencyKey() != null) {
            var existing = auditRepo.findByIdempotencyKey(req.idempotencyKey());
            if (existing.isPresent()) {
                OrderAudit a = existing.get();
                return Mono.just(new OrderDtos.SubmittedOrderResponse(a.getOrderHash(), a.getStatus(), null));
            }
        }
        BuiltOrder built = pending.get(userId, req.orderHash());
        if (built == null) {
            throw ApiException.notFound("ORDER_NOT_FOUND",
                    "Prepared order is not in cache (expired or unknown). Re-prepare.");
        }
        AppUser user = userRepo.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("USER_GONE", "User not found"));
        ClobCredentials creds = credentialsStore.get(userId);

        String signature;
        try {
            signature = builder.finalizeSignature(built, req.signature());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_SIGNATURE", e.getMessage());
        }

        OrderAudit audit = new OrderAudit();
        audit.setUserId(userId);
        audit.setOrderHash(req.orderHash());
        // wireOrder, not typedData.message: for POLY_1271 the signed message is the ERC-7739 wrapper.
        java.util.Map<?, ?> order = built.wireOrder() != null
                ? built.wireOrder()
                : (java.util.Map<?, ?>) built.typedData().get("message");
        audit.setConditionId(built.conditionId());
        audit.setTokenId(order.get("tokenId").toString());
        audit.setSide(sideName(order.get("side")));
        audit.setMakerAmount(built.makerAmount());
        audit.setTakerAmount(built.takerAmount());
        audit.setStatus("SUBMITTING");
        audit.setIdempotencyKey(req.idempotencyKey());
        auditRepo.save(audit);

        return clob.submit(built, signature, user.getWalletAddress(), creds)
                .doOnSuccess(resp -> {
                    audit.setStatus(resp.status());
                    auditRepo.save(audit);
                    pending.invalidate(userId, req.orderHash());
                    log.info("Order submitted user={} hash={} status={}", userId, req.orderHash(), resp.status());
                })
                .doOnError(err -> {
                    audit.setStatus("FAILED");
                    audit.setErrorMessage(err.getMessage());
                    auditRepo.save(audit);
                    log.warn("Order submission failed user={} hash={}: {}", userId, req.orderHash(), err.toString());
                });
    }
}
