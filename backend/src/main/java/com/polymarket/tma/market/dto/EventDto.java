package com.polymarket.tma.market.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * A Polymarket event: one question with several markets, e.g. a date ladder ("…by Oct 31 / Nov 30")
 * or candidates. Titles come in the requested locale where Gamma has a translation.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventDto(String id, String slug, String title, List<MarketDto> markets) {
}
