package com.polymarket.tma.trading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polymarket.tma.config.AppProperties;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.web.reactive.function.client.WebClient;

class PositionsClientCacheTest {

    @Test
    void cacheHitMapsBecomePositions() {
        AppProperties props = mock(AppProperties.class, Answers.RETURNS_DEEP_STUBS);
        when(props.polymarket().dataBaseUrl()).thenReturn("http://localhost");
        PositionsClient client = new PositionsClient(WebClient.builder(), props, null, new ObjectMapper());

        Map<String, Object> cached = new LinkedHashMap<>();
        cached.put("conditionId", "0xabc");
        cached.put("size", 5);
        cached.put("redeemable", true);
        cached.put("someNewField", "ignored");

        List<PositionsClient.Position> out = client.toPositions(List.of(cached));
        assertThat(out).hasSize(1);
        assertThat(out.get(0).conditionId()).isEqualTo("0xabc");
        assertThat(out.get(0).size()).isEqualByComparingTo(BigDecimal.valueOf(5));
        assertThat(out.get(0).redeemable()).isTrue();
    }
}
