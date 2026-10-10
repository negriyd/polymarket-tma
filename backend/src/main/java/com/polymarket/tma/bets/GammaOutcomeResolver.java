package com.polymarket.tma.bets;

import com.polymarket.tma.market.dto.MarketDto;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Derives the winning outcome index from a settled Gamma market. */
@Component
public class GammaOutcomeResolver {

    private static final BigDecimal WIN_THRESHOLD = new BigDecimal("0.99");

    /**
     * @return outcome index (0 or 1) when the market is closed and one side trades near $1.
     */
    public Optional<Integer> winningOutcome(MarketDto market) {
        if (market == null || !Boolean.TRUE.equals(market.closed())) {
            return Optional.empty();
        }
        List<String> prices = market.outcomePrices();
        if (prices == null || prices.size() < 2) {
            return Optional.empty();
        }
        for (int i = 0; i < prices.size(); i++) {
            try {
                if (new BigDecimal(prices.get(i)).compareTo(WIN_THRESHOLD) >= 0) {
                    return Optional.of(i);
                }
            } catch (NumberFormatException ignored) {
                // skip malformed price
            }
        }
        return Optional.empty();
    }
}
