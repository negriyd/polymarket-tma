package com.polymarket.tma.market.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderbookDto(
        @JsonProperty("market") String market,
        @JsonProperty("asset_id") String assetId,
        @JsonProperty("hash") String hash,
        List<Level> bids,
        List<Level> asks,
        @JsonProperty("timestamp") String timestamp
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Level(BigDecimal price, BigDecimal size) {}
}
