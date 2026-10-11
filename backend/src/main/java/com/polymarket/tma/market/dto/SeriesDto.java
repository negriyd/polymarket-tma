package com.polymarket.tma.market.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.time.Instant;
import java.util.List;

/** Gamma series: recurring events of one question, e.g. a new date ladder every month. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SeriesDto(String id, String title, List<Event> events) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Event(
            String id,
            String title,
            @JsonDeserialize(using = PolymarketInstantDeserializer.class) Instant endDate,
            Boolean closed) {
    }
}
