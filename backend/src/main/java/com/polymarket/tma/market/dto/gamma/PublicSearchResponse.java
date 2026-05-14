package com.polymarket.tma.market.dto.gamma;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.polymarket.tma.market.dto.MarketDto;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PublicSearchResponse(
        List<GammaSearchEvent> events,
        SearchPagination pagination
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SearchPagination(Boolean hasMore, Integer totalResults) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GammaSearchEvent(List<MarketDto> markets) {}
}
