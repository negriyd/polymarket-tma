package com.polymarket.tma.bets;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class MarketPoolServiceTest {

    @Test
    void impliedProbabilityReturnsShareOfPool() {
        assertThat(MarketPoolService.impliedProbability(
                BigInteger.valueOf(60_000_000),
                BigInteger.valueOf(150_000_000)))
                .isEqualTo("0.400000");
    }

    @Test
    void impliedProbabilityZeroWhenPoolEmpty() {
        assertThat(MarketPoolService.impliedProbability(
                BigInteger.ZERO,
                BigInteger.valueOf(100)))
                .isEqualTo("0");
    }

    @Test
    void toUsdcStringFormatsMicroUnits() {
        assertThat(MarketPoolService.toUsdcString(BigInteger.valueOf(5_500_000)))
                .isEqualTo("5.500000");
    }
}
