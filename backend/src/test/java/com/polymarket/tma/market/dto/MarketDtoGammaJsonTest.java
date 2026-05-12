package com.polymarket.tma.market.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Ensures {@link MarketDto} still matches live Gamma API payloads (regression guard for INTERNAL_ERROR from decode failures).
 */
class MarketDtoGammaJsonTest {

    @Test
    void deserializesLiveGammaMarketsJson() throws Exception {
        ObjectMapper om = new ObjectMapper().registerModule(new JavaTimeModule());
        try (InputStream in = getClass().getResourceAsStream("/gamma-markets-sample.json")) {
            assertThat(in).isNotNull();
            List<MarketDto> list = om.readValue(in, new TypeReference<>() {});
            assertThat(list).isNotEmpty();
            MarketDto m = list.getFirst();
            assertThat(m.conditionId()).isNotBlank();
            assertThat(m.outcomes()).hasSize(2);
        }
    }
}
