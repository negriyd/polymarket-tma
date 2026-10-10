package com.polymarket.tma.bets;

import static org.assertj.core.api.Assertions.assertThat;

import com.polymarket.tma.market.dto.MarketDto;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PayoutCalculatorTest {

    @Test
    void applyBpsComputesPlatformFee() {
        assertThat(PayoutCalculator.applyBps(BigInteger.valueOf(150_000_000L), 500))
                .isEqualTo(BigInteger.valueOf(7_500_000L));
    }

    @Test
    void shareOfDistributesParimutuelPayout() {
        // Alice $60 of $100 winning side, payout pool $142.5 => $85.5
        BigInteger payoutPool = BigInteger.valueOf(142_500_000L);
        BigInteger winningTotal = BigInteger.valueOf(100_000_000L);
        BigInteger alice = BigInteger.valueOf(60_000_000L);
        assertThat(PayoutCalculator.shareOf(alice, payoutPool, winningTotal))
                .isEqualTo(BigInteger.valueOf(85_500_000L));
    }
}
