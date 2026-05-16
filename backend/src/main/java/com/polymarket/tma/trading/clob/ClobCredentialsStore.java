package com.polymarket.tma.trading.clob;

import java.time.Duration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Stores CLOB API credentials per user in Redis with a long TTL. Long-lived keys are still
 * subject to rotation server-side; clients re-derive when calls return 401.
 */
@Component
public class ClobCredentialsStore {

    private static final String PREFIX = "pm:clob-creds:";
    private static final Duration TTL = Duration.ofDays(30);

    private final RedisTemplate<String, Object> redis;

    public ClobCredentialsStore(RedisTemplate<String, Object> redis) {
        this.redis = redis;
    }

    public void put(long userId, ClobCredentials creds) {
        redis.opsForValue().set(key(userId), creds, TTL);
    }

    public ClobCredentials get(long userId) {
        Object v = redis.opsForValue().get(key(userId));
        return (v instanceof ClobCredentials cc) ? cc : null;
    }

    public void invalidate(long userId) {
        redis.delete(key(userId));
    }

    public boolean exists(long userId) {
        return Boolean.TRUE.equals(redis.hasKey(key(userId)));
    }

    private static String key(long userId) {
        return PREFIX + userId;
    }
}
