package com.polymarket.tma.trading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polymarket.tma.trading.OrderBuilder.BuiltOrder;
import java.math.BigInteger;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * Regression test for the cache-miss bug that caused {@code POST /api/orders/submit} to fail with
 * "Prepared order is not in cache". Earlier, the global Redis serializer dropped the
 * {@code @class} hint and the cached value came back as {@link LinkedHashMap}, so the old
 * {@code instanceof BuiltOrder} check returned {@code null}.
 */
@ExtendWith(MockitoExtension.class)
class PendingOrderCacheTest {

    @SuppressWarnings("unchecked")
    @Mock private RedisTemplate<String, Object> redis;
    @SuppressWarnings("unchecked")
    @Mock private ValueOperations<String, Object> valueOps;

    private final ObjectMapper mapper = new ObjectMapper();
    private PendingOrderCache cache;

    private final Map<String, Object> backing = new HashMap<>();

    @BeforeEach
    void setUp() {
        cache = new PendingOrderCache(redis, mapper);
        // Lenient: not every test exercises both set + get, but stubbing both keeps the suite
        // readable. The strict Mockito extension would otherwise complain on read-only tests.
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
        lenient().doAnswer(inv -> {
            String key = inv.getArgument(0, String.class);
            Object value = inv.getArgument(1);
            backing.put(key, value);
            return null;
        }).when(valueOps).set(any(String.class), any(), any(Duration.class));
        lenient().when(valueOps.get(any(String.class))).thenAnswer(inv ->
                backing.get(inv.getArgument(0, String.class)));
    }

    @Test
    void roundTripsBuiltOrderAcrossRedisJsonStringStorage() {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("tokenId", "13915689317269078219168496739008737517740566192006337297676041270492637394586");
        message.put("side", "0");
        Map<String, Object> typedData = new LinkedHashMap<>();
        typedData.put("primaryType", "Order");
        typedData.put("message", message);

        Map<String, Object> wireOrder = new LinkedHashMap<>(message);
        wireOrder.put("expiration", "0");

        BuiltOrder original = new BuiltOrder(
                "0x" + "ab".repeat(32),
                new BigInteger("123456789012345678"),
                new BigInteger("987654321098765432"),
                typedData,
                wireOrder);

        cache.put(7L, original);
        BuiltOrder loaded = cache.get(7L, original.orderHash());

        assertThat(loaded).isNotNull();
        assertThat(loaded.orderHash()).isEqualTo(original.orderHash());
        assertThat(loaded.makerAmount()).isEqualTo(original.makerAmount());
        assertThat(loaded.takerAmount()).isEqualTo(original.takerAmount());
        @SuppressWarnings("unchecked")
        Map<String, Object> loadedMessage = (Map<String, Object>) loaded.typedData().get("message");
        assertThat(loadedMessage.get("tokenId")).isEqualTo(message.get("tokenId"));
    }

    @Test
    void getReturnsNullOnUnknownHash() {
        assertThat(cache.get(1L, "0xdead")).isNull();
    }

    @Test
    void invalidateRemovesValue() {
        Map<String, Object> typedData = new LinkedHashMap<>();
        typedData.put("primaryType", "Order");
        typedData.put("message", new LinkedHashMap<String, Object>());

        BuiltOrder order = new BuiltOrder(
                "0xfeed", BigInteger.ZERO, BigInteger.ZERO, typedData, new LinkedHashMap<>());
        cache.put(1L, order);
        assertThat(cache.get(1L, "0xfeed")).isNotNull();

        // RedisTemplate#delete(K) returns Boolean. Wire the mock so we evict from the backing
        // map and the subsequent get returns null (Java's Map.get on absent key).
        when(redis.delete(eq("pm:pending-order:1:0xfeed"))).thenAnswer(inv -> {
            backing.remove(inv.getArgument(0, String.class));
            return Boolean.TRUE;
        });
        cache.invalidate(1L, "0xfeed");
        assertThat(cache.get(1L, "0xfeed")).isNull();
    }
}
