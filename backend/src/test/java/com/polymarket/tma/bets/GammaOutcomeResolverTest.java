package com.polymarket.tma.bets;

import static org.assertj.core.api.Assertions.assertThat;

import com.polymarket.tma.market.dto.MarketDto;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class GammaOutcomeResolverTest {

    private final GammaOutcomeResolver resolver = new GammaOutcomeResolver();

    @Test
    void returnsWinningIndexWhenMarketClosed() {
        MarketDto market = new MarketDto(
                "1", "0xabc", "Q?", null, null, null, null, null,
                Instant.now(), null, null, false, true, false, false,
                null, null, null,
                List.of("Yes", "No"), List.of("1", "2"), List.of("0.999", "0.001"), false);

        assertThat(resolver.winningOutcome(market)).contains(0);
    }

    @Test
    void emptyWhenMarketStillOpen() {
        MarketDto market = new MarketDto(
                "1", "0xabc", "Q?", null, null, null, null, null,
                Instant.now(), null, null, true, false, true, false,
                null, null, null,
                List.of("Yes", "No"), List.of("1", "2"), List.of("0.6", "0.4"), false);

        assertThat(resolver.winningOutcome(market)).isEmpty();
    }
}
