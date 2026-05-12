package com.polymarket.tma.trading;

import com.polymarket.tma.trading.OrderBuilder.BuiltOrder;
import java.time.Duration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Stores prepared orders briefly so they can be matched with the client signature on submission.
 * 5-minute TTL is plenty given the EIP-712 expiration is 10 minutes by default.
 */
@Component
public class PendingOrderCache {

    private static final String PREFIX = "pm:pending-order:";
    private static final Duration TTL = Duration.ofMinutes(5);

    private final RedisTemplate<String, Object> redis;

    public PendingOrderCache(RedisTemplate<String, Object> redis) {
        this.redis = redis;
    }

    public void put(long userId, BuiltOrder order) {
        redis.opsForValue().set(key(userId, order.orderHash()), order, TTL);
    }

    public BuiltOrder get(long userId, String orderHash) {
        Object v = redis.opsForValue().get(key(userId, orderHash));
        return (v instanceof BuiltOrder bo) ? bo : null;
    }

    public void invalidate(long userId, String orderHash) {
        redis.delete(key(userId, orderHash));
    }

    private static String key(long userId, String orderHash) {
        return PREFIX + userId + ":" + orderHash;
    }
}
