package com.polymarket.tma.market.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MarketDto(
        /** Gamma internal market id (stringified); required for {@code GET /markets/{id}} on Gamma. */
        @JsonProperty("id") String id,
        @JsonProperty("conditionId") String conditionId,
        String question,
        String slug,
        String description,
        String category,
        String image,
        String icon,
        @JsonProperty("endDate")
        @JsonDeserialize(using = PolymarketInstantDeserializer.class)
        Instant endDate,
        @JsonProperty("gameStartTime")
        @JsonDeserialize(using = PolymarketInstantDeserializer.class)
        Instant gameStartTime,
        @JsonProperty("marketSlug") String marketSlug,
        Boolean active,
        Boolean closed,
        @JsonProperty("acceptingOrders") Boolean acceptingOrders,
        Boolean archived,
        BigDecimal volume,
        @JsonProperty("volume24hr") BigDecimal volume24h,
        BigDecimal liquidity,
        @JsonDeserialize(using = PolymarketStringListDeserializer.class)
        List<String> outcomes,
        @JsonProperty("clobTokenIds")
        @JsonDeserialize(using = PolymarketStringListDeserializer.class)
        List<String> clobTokenIds,
        @JsonProperty("outcomePrices")
        @JsonDeserialize(using = PolymarketStringListDeserializer.class)
        List<String> outcomePrices
) {
}
