package com.polymarket.tma.trading.relayer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Prepared deposit-wallet batches awaiting the owner's signature. The server keeps the calls so a
 * client can only get our builder credentials to relay batches we built (approvals), never
 * arbitrary calldata. Plain JSON strings for the same reason as {@code PendingOrderCache}.
 */
@Component
public class PendingBatchCache {

    private static final Logger log = LoggerFactory.getLogger(PendingBatchCache.class);

    private static final String PREFIX = "pm:pending-batch:";

    public record PendingBatch(
            String batchHash,
            /** APPROVALS / TRADING_FEE / REDEEM — for logs. */
            String purpose,
            String owner,
            String depositWallet,
            String nonce,
            String deadline,
            List<DepositWalletBatch.Call> calls) {}

    private final RedisTemplate<String, Object> redis;
    private final ObjectMapper objectMapper;

    public PendingBatchCache(RedisTemplate<String, Object> redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public void put(long userId, PendingBatch batch, Duration ttl) {
        try {
            redis.opsForValue().set(key(userId, batch.batchHash()), objectMapper.writeValueAsString(batch), ttl);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize pending batch", e);
        }
    }

    public PendingBatch get(long userId, String batchHash) {
        Object raw = redis.opsForValue().get(key(userId, batchHash));
        if (raw == null) {
            return null;
        }
        try {
            return objectMapper.readValue(raw instanceof String s ? s : raw.toString(), PendingBatch.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize pending batch user={} hash={}: {}", userId, batchHash, e.toString());
            return null;
        }
    }

    public void invalidate(long userId, String batchHash) {
        redis.delete(key(userId, batchHash));
    }

    private static String key(long userId, String batchHash) {
        return PREFIX + userId + ":" + batchHash.toLowerCase();
    }
}
