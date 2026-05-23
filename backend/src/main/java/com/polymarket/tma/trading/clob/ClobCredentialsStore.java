package com.polymarket.tma.trading.clob;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Stores CLOB API credentials per user in Redis with a long TTL. Long-lived keys are still
 * subject to rotation server-side; clients re-derive when calls return 401.
 *
 * <p>Values are written as <strong>plain JSON strings</strong> (not via the global
 * {@code GenericJackson2JsonRedisSerializer}) and parsed back into {@link ClobCredentials} via
 * {@link ObjectMapper}. The previous instanceof-based read path silently returned {@code null}
 * because the shared serializer hands back a {@link java.util.LinkedHashMap} when the configured
 * mapper has no default typing — which meant L2 HMAC headers were never attached on subsequent
 * order submissions.
 */
@Component
public class ClobCredentialsStore {

    private static final Logger log = LoggerFactory.getLogger(ClobCredentialsStore.class);

    private static final String PREFIX = "pm:clob-creds:";
    private static final Duration TTL = Duration.ofDays(30);

    private final RedisTemplate<String, Object> redis;
    private final ObjectMapper objectMapper;

    public ClobCredentialsStore(RedisTemplate<String, Object> redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public void put(long userId, ClobCredentials creds) {
        try {
            String json = objectMapper.writeValueAsString(creds);
            redis.opsForValue().set(key(userId), json, TTL);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize CLOB credentials to JSON", e);
        }
    }

    public ClobCredentials get(long userId) {
        Object raw = redis.opsForValue().get(key(userId));
        if (raw == null) {
            return null;
        }
        String json = raw instanceof String s ? s : raw.toString();
        try {
            return objectMapper.readValue(json, ClobCredentials.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize cached CLOB creds for user={}: {}", userId, e.toString());
            return null;
        }
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
