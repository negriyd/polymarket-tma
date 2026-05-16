package com.polymarket.tma.trading.clob;

import com.polymarket.tma.auth.entity.AppUser;
import com.polymarket.tma.auth.repo.AppUserRepository;
import com.polymarket.tma.common.ApiException;
import com.polymarket.tma.trading.dto.ClobAuthDtos;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Orchestrates the L1 CLOB key derivation. Holds a short-lived in-memory map of
 * {@code (userId → BuiltAuth)} so the client can echo the timestamp/nonce on submit without
 * re-fetching from Redis. Keys are deleted after submission or short TTL.
 */
@Service
public class ClobAuthService {

    private static final Duration PREPARE_TTL = Duration.ofMinutes(5);

    private final ClobAuthBuilder builder;
    private final ClobApiKeyClient apiKeyClient;
    private final ClobCredentialsStore store;
    private final AppUserRepository userRepo;

    private final ConcurrentHashMap<Long, PreparedEntry> prepared = new ConcurrentHashMap<>();

    public ClobAuthService(ClobAuthBuilder builder,
                           ClobApiKeyClient apiKeyClient,
                           ClobCredentialsStore store,
                           AppUserRepository userRepo) {
        this.builder = builder;
        this.apiKeyClient = apiKeyClient;
        this.store = store;
        this.userRepo = userRepo;
    }

    public ClobAuthDtos.PrepareResponse prepare(long userId) {
        AppUser user = userRepo.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("USER_GONE", "User not found"));
        if (user.getWalletAddress() == null || user.getWalletAddress().isBlank()) {
            throw ApiException.badRequest("WALLET_REQUIRED",
                    "Wallet address must be set before deriving CLOB credentials");
        }
        ClobAuthBuilder.BuiltAuth built = builder.build(user.getWalletAddress());
        prepared.put(userId, new PreparedEntry(built, System.currentTimeMillis()));
        return new ClobAuthDtos.PrepareResponse(
                built.address(),
                built.timestampSec(),
                built.nonce(),
                built.digestHex(),
                built.typedData());
    }

    public Mono<ClobAuthDtos.StatusResponse> submit(long userId, ClobAuthDtos.SubmitRequest req) {
        AppUser user = userRepo.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("USER_GONE", "User not found"));
        if (user.getWalletAddress() == null || user.getWalletAddress().isBlank()) {
            throw ApiException.badRequest("WALLET_REQUIRED",
                    "Wallet address must be set before submitting CLOB signature");
        }
        evictExpired();
        PreparedEntry entry = prepared.remove(userId);
        if (entry == null) {
            throw ApiException.notFound("CLOB_AUTH_NOT_PREPARED",
                    "No prepared CLOB auth payload for this user. Call /prepare first.");
        }
        ClobAuthBuilder.BuiltAuth built = entry.built;
        if (built.timestampSec() != req.timestamp() || built.nonce() != req.nonce()) {
            throw ApiException.badRequest("CLOB_AUTH_MISMATCH",
                    "Signature payload (timestamp / nonce) does not match the prepared one");
        }
        return apiKeyClient.deriveKey(user.getWalletAddress(), req.timestamp(), req.nonce(), req.signature())
                .doOnNext(creds -> store.put(userId, creds))
                .map(creds -> new ClobAuthDtos.StatusResponse(true));
    }

    public ClobAuthDtos.StatusResponse status(long userId) {
        return new ClobAuthDtos.StatusResponse(store.exists(userId));
    }

    public void revoke(long userId) {
        store.invalidate(userId);
    }

    private void evictExpired() {
        long cutoff = System.currentTimeMillis() - PREPARE_TTL.toMillis();
        prepared.entrySet().removeIf(e -> e.getValue().createdAtMillis < cutoff);
    }

    private record PreparedEntry(ClobAuthBuilder.BuiltAuth built, long createdAtMillis) {}
}
