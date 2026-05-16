package com.polymarket.tma.trading;

import com.polymarket.tma.auth.entity.AppUser;
import com.polymarket.tma.auth.repo.AppUserRepository;
import com.polymarket.tma.common.ApiException;
import com.polymarket.tma.trading.OrderBuilder.BuiltOrder;
import com.polymarket.tma.trading.clob.ClobCredentials;
import com.polymarket.tma.trading.clob.ClobCredentialsStore;
import com.polymarket.tma.trading.dto.OrderDtos;
import com.polymarket.tma.trading.entity.OrderAudit;
import com.polymarket.tma.trading.repo.OrderAuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

@Service
public class TradingService {

    private static final Logger log = LoggerFactory.getLogger(TradingService.class);

    private final OrderBuilder builder;
    private final PendingOrderCache pending;
    private final AppUserRepository userRepo;
    private final OrderAuditRepository auditRepo;
    private final ClobOrderClient clob;
    private final ClobCredentialsStore credentialsStore;

    public TradingService(OrderBuilder builder,
                          PendingOrderCache pending,
                          AppUserRepository userRepo,
                          OrderAuditRepository auditRepo,
                          ClobOrderClient clob,
                          ClobCredentialsStore credentialsStore) {
        this.builder = builder;
        this.pending = pending;
        this.userRepo = userRepo;
        this.auditRepo = auditRepo;
        this.clob = clob;
        this.credentialsStore = credentialsStore;
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
        BuiltOrder built = builder.build(maker, signer, req);
        pending.put(userId, built);
        return new OrderDtos.TypedDataResponse(built.orderHash(), built.typedData());
    }

    /**
     * Resolve the {@code maker} for the EIP-712 order:
     * <ul>
     *     <li>{@link OrderDtos.SignatureType#EOA} (default): always the wallet address (maker == signer).</li>
     *     <li>{@code POLY_PROXY} / {@code POLY_GNOSIS_SAFE}: requires {@code makerAddress} in the request
     *         (the Polymarket proxy / Safe holding the funds). It must differ from the signer.</li>
     * </ul>
     */
    private static String resolveMaker(AppUser user, OrderDtos.PrepareOrderRequest req) {
        OrderDtos.SignatureType type = req.signatureType() == null
                ? OrderDtos.SignatureType.EOA
                : req.signatureType();
        if (type == OrderDtos.SignatureType.EOA) {
            return user.getWalletAddress();
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

        OrderAudit audit = new OrderAudit();
        audit.setUserId(userId);
        audit.setOrderHash(req.orderHash());
        audit.setConditionId(((java.util.Map<?, ?>) built.typedData().get("message")).get("tokenId").toString());
        audit.setSide(((java.util.Map<?, ?>) built.typedData().get("message")).get("side").toString());
        audit.setMakerAmount(built.makerAmount());
        audit.setTakerAmount(built.takerAmount());
        audit.setStatus("SUBMITTING");
        audit.setIdempotencyKey(req.idempotencyKey());
        auditRepo.save(audit);

        return clob.submit(built, req.signature(), user.getWalletAddress(), creds)
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
