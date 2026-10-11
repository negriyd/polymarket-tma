package com.polymarket.tma.market;

import static org.assertj.core.api.Assertions.assertThat;

import com.polymarket.tma.market.dto.EventDto;
import com.polymarket.tma.market.dto.MarketDto;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarketServiceEventOrderTest {

    private static MarketDto market(String label, String end, String yes, boolean closed) {
        return new MarketDto("1", "0x" + label, "Q " + label, null, null, null, null, null,
                end == null ? null : Instant.parse(end), null, null, true, closed, !closed, false, null, null, null,
                List.of("Yes", "No"), List.of("1", "2"), List.of(yes, "0"), true, null, null, label);
    }

    @Test
    void dateLadderIsChronologicalAndHidesClosedMarkets() {
        // Gamma's groupItemThreshold order: dates appended later come last.
        var event = new EventDto("1", "s", "Ceasefire by…?", List.of(
                market("Jul 27", "2026-07-27T00:00:00Z", "0", true),
                market("Oct 31", "2026-10-31T00:00:00Z", "0.78", false),
                market("Dec 31", "2026-12-31T00:00:00Z", "0.34", false),
                market("Oct 12", "2026-10-12T00:00:00Z", "0.96", false)));

        assertThat(MarketService.openMarketsInOrder(event).markets())
                .extracting(MarketDto::groupItemTitle)
                .containsExactly("Oct 12", "Oct 31", "Dec 31");
    }

    @Test
    void sameDateMarketsAreOrderedByYesPrice() {
        String end = "2028-11-07T00:00:00Z";
        var event = new EventDto("2", "s", "Nominee 2028", List.of(
                market("Buttigieg", end, "0.05", false),
                market("Newsom", end, "0.31", false),
                market("Ocasio-Cortez", end, "0.12", false)));

        assertThat(MarketService.openMarketsInOrder(event).markets())
                .extracting(MarketDto::groupItemTitle)
                .containsExactly("Newsom", "Ocasio-Cortez", "Buttigieg");
    }

    @Test
    void resolvedMarketsAreKeptSeparatelyLatestFirst() {
        var event = new EventDto("1", "s", "Ceasefire by…?", List.of(
                market("Aug 22", "2026-08-22T00:00:00Z", "1", true),
                market("Oct 12", "2026-10-12T00:00:00Z", "0.96", false),
                market("Aug 31", "2026-08-31T00:00:00Z", "0", true)));

        var out = MarketService.openMarketsInOrder(event);
        assertThat(out.markets()).extracting(MarketDto::groupItemTitle).containsExactly("Oct 12");
        assertThat(out.resolved()).extracting(MarketDto::groupItemTitle).containsExactly("Aug 31", "Aug 22");
    }

    @Test
    void seriesChipsArePastThenOpenInDateOrder() {
        var events = List.of(
                new com.polymarket.tma.market.dto.SeriesDto.Event("a", "Jul", Instant.parse("2026-07-31T00:00:00Z"), true, null),
                new com.polymarket.tma.market.dto.SeriesDto.Event("c", "Nov", Instant.parse("2026-11-03T00:00:00Z"), false, null),
                new com.polymarket.tma.market.dto.SeriesDto.Event("b", "Oct", Instant.parse("2026-10-31T00:00:00Z"), false, null),
                new com.polymarket.tma.market.dto.SeriesDto.Event("z", "Sep", Instant.parse("2026-09-30T00:00:00Z"), true, null));

        assertThat(MarketService.pickSeriesEvents(events, "b"))
                .extracting(com.polymarket.tma.market.dto.SeriesDto.Event::id)
                .containsExactly("a", "z", "b", "c");
    }
}
