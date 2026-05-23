package com.polymarket.tma.trading;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polymarket.tma.trading.OrderBuilder.BuiltOrder;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Stores prepared orders briefly so they can be matched with the client signature on submission.
 *
 * <p>Values are written as <strong>plain JSON strings</strong> (not via the global
 * {@link org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer}) and parsed
 * back into {@link BuiltOrder} typed. This sidesteps the default-typing pitfall: the shared
 * {@code GenericJackson2JsonRedisSerializer} bean was constructed with an external
 * {@link ObjectMapper} that does not embed {@code @class}, so a previous {@code instanceof
 * BuiltOrder} check on the deserialized {@link java.util.LinkedHashMap} always returned
 * {@code false} → submit failed with {@code ORDER_NOT_FOUND}.
 *
 * <p>5-minute TTL is plenty given the EIP-712 expiration is 10 minutes by default.
 */
@Component
public class PendingOrderCache {

    private static final Logger log = LoggerFactory.getLogger(PendingOrderCache.class);

    private static final String PREFIX = "pm:pending-order:";
    private static final Duration TTL = Duration.ofMinutes(5);

    private final RedisTemplate<String, Object> redis;
    private final ObjectMapper objectMapper;

    public PendingOrderCache(RedisTemplate<String, Object> redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public void put(long userId, BuiltOrder order) {
        try {
            String json = objectMapper.writeValueAsString(order);
            redis.opsForValue().set(key(userId, order.orderHash()), json, TTL);
        } catch (JsonProcessingException e) {
            // Should never happen for our DTO. Bubble up so the caller can surface a 500.
            throw new IllegalStateException("Failed to serialize prepared order to JSON", e);
        }
    }

    public BuiltOrder get(long userId, String orderHash) {
        Object raw = redis.opsForValue().get(key(userId, orderHash));
        if (raw == null) {
            return null;
        }
        String json = raw instanceof String s ? s : raw.toString();
        try {
            return objectMapper.readValue(json, BuiltOrder.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize cached prepared order for user={} hash={}: {}",
                    userId, orderHash, e.toString());
            return null;
        }
    }

    public void invalidate(long userId, String orderHash) {
        redis.delete(key(userId, orderHash));
    }

    private static String key(long userId, String orderHash) {
        return PREFIX + userId + ":" + orderHash;
    }
}
