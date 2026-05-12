package com.polymarket.tma.market.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PolymarketInstantDeserializerTest {

    @Test
    void normalizesSpaceBetweenDateAndTime() {
        assertThat(PolymarketInstantDeserializer.normalizeSpaceDateTime("2026-05-12 12:25:00Z"))
                .isEqualTo("2026-05-12T12:25:00Z");
        assertThat(PolymarketInstantDeserializer.normalizeSpaceDateTime("2026-05-31T00:00:00Z"))
                .isEqualTo("2026-05-31T00:00:00Z");
    }

    @Test
    void marketDtoParsesGammaStyleEndDate() throws Exception {
        ObjectMapper om = new ObjectMapper().registerModule(new JavaTimeModule());
        String json = """
                {
                  "id": "42",
                  "conditionId": "0xabc",
                  "question": "q",
                  "slug": "slug",
                  "endDate": "2026-05-12 12:25:00Z",
                  "outcomes": "[\\"Yes\\", \\"No\\"]",
                  "clobTokenIds": "[\\"a\\",\\"b\\"]",
                  "outcomePrices": "[\\"0.5\\", \\"0.5\\"]"
                }
                """;
        MarketDto m = om.readValue(json, MarketDto.class);
        assertThat(m.endDate()).isEqualTo(Instant.parse("2026-05-12T12:25:00Z"));
    }
}
