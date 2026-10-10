package com.polymarket.tma.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.polymarket.tma.config.AppProperties;
import com.polymarket.tma.market.client.ClobClient;
import com.polymarket.tma.market.client.GammaClient;
import com.polymarket.tma.market.dto.MarketDto;
import com.polymarket.tma.market.dto.MarketListResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class MarketServiceListCacheTest {

    @Mock private GammaClient gamma;
    @Mock private ClobClient clob;
    @Mock private MarketCacheService cache;
    @Mock private AppProperties props;

    private MarketService service;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        service = new MarketService(gamma, clob, cache, props, mapper);
        when(props.polymarket()).thenReturn(new AppProperties.Polymarket(
                "https://gamma", "https://clob", "https://data", "wss://ws",
                Duration.ofSeconds(45), Duration.ofSeconds(8), Duration.ofSeconds(3), Duration.ofMinutes(2)));
        when(gamma.listMarkets(anyInt(), anyInt(), anyString(), anyBoolean(), any(), any()))
                .thenReturn(Mono.just(List.of()));
    }

    @Test
    void listNormalizesLinkedHashMapItemsFromRedisCache() {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("id", "1");
        map.put("conditionId", "0xabc");
        map.put("question", "Will it rain?");
        map.put("active", true);
        map.put("closed", false);

        when(cache.readThrough(any(), eq(List.class), any(), any()))
                .thenReturn(Mono.just(List.of(map)));

        StepVerifier.create(service.list(0, 10, "volume24hr", false, null, null))
                .assertNext(resp -> {
                    assertThat(resp.items()).hasSize(1);
                    assertThat(resp.items().get(0).question()).isEqualTo("Will it rain?");
                    assertThat(resp.items().get(0)).isInstanceOf(MarketDto.class);
                })
                .verifyComplete();
    }

    @Test
    void listUsesConcreteArrayListNotSubList() {
        MarketDto m1 = new MarketDto(
                "1", "0xa", "Q1?", null, null, null, null, null,
                Instant.now(), null, null, true, false, true, false,
                null, null, null, List.of("Yes", "No"), List.of("1", "2"), List.of("0.5", "0.5"), false);
        MarketDto m2 = new MarketDto(
                "2", "0xb", "Q2?", null, null, null, null, null,
                Instant.now(), null, null, true, false, true, false,
                null, null, null, List.of("Yes", "No"), List.of("1", "2"), List.of("0.5", "0.5"), false);

        when(cache.readThrough(any(), eq(List.class), any(), any()))
                .thenReturn(Mono.just(List.of(m1, m2)));

        StepVerifier.create(service.list(0, 1, "volume24hr", false, null, null))
                .assertNext(resp -> {
                    assertThat(resp.hasMore()).isTrue();
                    assertThat(resp.items()).hasSize(1);
                    assertThat(resp.items().get(0).question()).isEqualTo("Q1?");
                })
                .verifyComplete();
    }
}
