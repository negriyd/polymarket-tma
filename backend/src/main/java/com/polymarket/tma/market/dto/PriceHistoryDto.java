package com.polymarket.tma.market.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PriceHistoryDto(List<Point> history) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Point(Long t, BigDecimal p) {}
}
