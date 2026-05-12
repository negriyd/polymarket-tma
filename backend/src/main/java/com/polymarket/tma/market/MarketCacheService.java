package com.polymarket.tma.market;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** Read-through cache backed by Redis with per-key TTL. */
@Component
public class MarketCacheService {

    private static final Logger log = LoggerFactory.getLogger(MarketCacheService.class);

    private final RedisTemplate<String, Object> redis;

    public MarketCacheService(RedisTemplate<String, Object> redis) {
        this.redis = redis;
    }

    public <T> Mono<T> readThrough(String key, Class<T> type, Duration ttl, Mono<T> loader) {
        return Mono.defer(() -> {
            try {
                Object cached = redis.opsForValue().get(key);
                if (cached != null && type.isInstance(cached)) {
                    return Mono.just(type.cast(cached));
                }
            } catch (RuntimeException ex) {
                // Without this, a down Redis blows up the whole Mono chain → 500 INTERNAL_ERROR for read APIs.
                log.warn("Redis get failed for {}: {}", key, ex.toString());
            }
            return loader.flatMap(value -> Mono.fromRunnable(() -> {
                try {
                    redis.opsForValue().set(key, value, ttl);
                } catch (RuntimeException ex) {
                    log.warn("Redis set failed for {}: {}", key, ex.toString());
                }
            }).thenReturn(value));
        });
    }

    public void invalidate(String key) {
        redis.delete(key);
    }
}
