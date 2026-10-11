package com.polymarket.tma.market.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.List;

/**
 * A Polymarket event: one question with several markets, e.g. a date ladder ("…by Oct 31 / Nov 30")
 * or candidates. Titles come in the requested locale where Gamma has a translation.
 *
 * @param markets      open markets, in display order
 * @param resolved     closed markets, latest first ("View resolved")
 * @param series       Gamma's series reference (input only)
 * @param seriesEvents sibling events of the same series (other months), for the date chips
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventDto(String id, String slug, String title, List<MarketDto> markets,
                       List<MarketDto> resolved, List<SeriesRef> series, List<SeriesEvent> seriesEvents) {

    public EventDto(String id, String slug, String title, List<MarketDto> markets) {
        this(id, slug, title, markets, null, null, null);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SeriesRef(String id) {}

    /**
     * @param marketKey market to open for this event; null for the current event
     */
    public record SeriesEvent(String id, String title, Instant endDate, Instant closedTime, boolean closed,
                              boolean current, String marketKey) {}
}
