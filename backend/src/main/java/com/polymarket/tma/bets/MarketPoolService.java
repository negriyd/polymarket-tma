package com.polymarket.tma.bets;

import com.polymarket.tma.bets.dto.BetDtos;
import com.polymarket.tma.bets.entity.Bet;
import com.polymarket.tma.bets.repo.BetRepository;
import com.polymarket.tma.config.AppProperties;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Aggregates confirmed bet stakes per outcome for parimutuel pool display.
 */
@Service
public class MarketPoolService {

    private static final BigInteger USDC_MICRO = BigInteger.valueOf(1_000_000L);

    private final BetRepository betRepo;
    private final AppProperties props;

    public MarketPoolService(BetRepository betRepo, AppProperties props) {
        this.betRepo = betRepo;
        this.props = props;
    }

    public BetDtos.PoolResponse pool(String conditionId, List<String> outcomeLabels) {
        BigInteger side0 = betRepo.sumAmountMicroByConditionAndOutcomeAndStatus(
                conditionId, (short) 0, Bet.STATUS_CONFIRMED);
        BigInteger side1 = betRepo.sumAmountMicroByConditionAndOutcomeAndStatus(
                conditionId, (short) 1, Bet.STATUS_CONFIRMED);
        if (side0 == null) {
            side0 = BigInteger.ZERO;
        }
        if (side1 == null) {
            side1 = BigInteger.ZERO;
        }

        BigInteger total = side0.add(side1);
        int feeBps = props.bets() == null ? 0 : props.bets().platformFeeBpsOrZero();

        List<BetDtos.PoolSide> sides = new ArrayList<>(2);
        sides.add(new BetDtos.PoolSide(
                0,
                label(outcomeLabels, 0),
                toUsdcString(side0),
                impliedProbability(side0, total)));
        sides.add(new BetDtos.PoolSide(
                1,
                label(outcomeLabels, 1),
                toUsdcString(side1),
                impliedProbability(side1, total)));

        return new BetDtos.PoolResponse(
                conditionId,
                toUsdcString(total),
                feeBps,
                sides);
    }

    /** Share of pool on this side (0..1), or "0" when pool empty. */
    static String impliedProbability(BigInteger sideMicro, BigInteger totalMicro) {
        if (totalMicro.signum() <= 0 || sideMicro.signum() <= 0) {
            return "0";
        }
        return new BigDecimal(sideMicro)
                .divide(new BigDecimal(totalMicro), 6, RoundingMode.HALF_UP)
                .toPlainString();
    }

    private static String label(List<String> outcomes, int index) {
        if (outcomes == null || index >= outcomes.size()) {
            return index == 0 ? "YES" : "NO";
        }
        return outcomes.get(index);
    }

    static String toUsdcString(BigInteger micro) {
        return new BigDecimal(micro)
                .divide(new BigDecimal(USDC_MICRO), 6, RoundingMode.HALF_UP)
                .toPlainString();
    }
}
