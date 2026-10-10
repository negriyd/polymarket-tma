package com.polymarket.tma.bets;

import com.polymarket.tma.bets.entity.Bet;
import com.polymarket.tma.bets.entity.Payout;
import com.polymarket.tma.bets.repo.BetRepository;
import com.polymarket.tma.bets.repo.PayoutRepository;
import com.polymarket.tma.config.AppProperties;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Parimutuel settlement: winners share {@code payout_pool = net_pool - platform_fee}.
 */
@Service
public class PayoutCalculator {

    private static final Logger log = LoggerFactory.getLogger(PayoutCalculator.class);

    private final BetRepository betRepo;
    private final PayoutRepository payoutRepo;
    private final UserBalanceService balanceService;
    private final AppProperties props;

    public PayoutCalculator(BetRepository betRepo,
                            PayoutRepository payoutRepo,
                            UserBalanceService balanceService,
                            AppProperties props) {
        this.betRepo = betRepo;
        this.payoutRepo = payoutRepo;
        this.balanceService = balanceService;
        this.props = props;
    }

    @Transactional
    public void settleMarket(String conditionId, short winningOutcomeIndex) {
        List<Bet> bets = betRepo.findByConditionIdAndStatus(conditionId, Bet.STATUS_CONFIRMED);
        if (bets.isEmpty()) {
            return;
        }

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

        BigInteger netPool = side0.add(side1);
        int feeBps = props.bets() == null ? 0 : props.bets().platformFeeBpsOrZero();
        BigInteger feeTotal = applyBps(netPool, feeBps);
        BigInteger payoutPool = netPool.subtract(feeTotal);
        BigInteger winningTotal = winningOutcomeIndex == 0 ? side0 : side1;

        if (winningTotal.signum() <= 0) {
            log.warn("Market {} resolved to outcome {} but winning side has zero stake", conditionId, winningOutcomeIndex);
            for (Bet bet : bets) {
                bet.setStatus(Bet.STATUS_LOST);
                betRepo.save(bet);
            }
            return;
        }

        for (Bet bet : bets) {
            if (payoutRepo.existsByBetId(bet.getId())) {
                continue;
            }
            if (bet.getOutcomeIndex() == winningOutcomeIndex) {
                BigInteger net = shareOf(bet.getAmountMicroUsdc(), payoutPool, winningTotal);
                BigInteger feeShare = shareOf(bet.getAmountMicroUsdc(), feeTotal, winningTotal);
                BigInteger gross = net.add(feeShare);
                creditWin(bet, gross, feeShare, net);
            } else {
                bet.setStatus(Bet.STATUS_LOST);
                betRepo.save(bet);
            }
        }
    }

    /** Full stake refund when a market is cancelled. */
    @Transactional
    public void refundMarket(String conditionId) {
        List<Bet> bets = betRepo.findByConditionIdAndStatus(conditionId, Bet.STATUS_CONFIRMED);
        for (Bet bet : bets) {
            if (payoutRepo.existsByBetId(bet.getId())) {
                continue;
            }
            BigInteger stake = bet.getAmountMicroUsdc();
            Payout payout = new Payout();
            payout.setBetId(bet.getId());
            payout.setUserId(bet.getUserId());
            payout.setGrossMicro(stake);
            payout.setFeeMicro(BigInteger.ZERO);
            payout.setNetMicro(stake);
            payoutRepo.save(payout);

            balanceService.credit(bet.getUserId(), stake);
            bet.setStatus(Bet.STATUS_REFUNDED);
            betRepo.save(bet);
        }
    }

    private void creditWin(Bet bet, BigInteger gross, BigInteger feeShare, BigInteger net) {
        Payout payout = new Payout();
        payout.setBetId(bet.getId());
        payout.setUserId(bet.getUserId());
        payout.setGrossMicro(gross);
        payout.setFeeMicro(feeShare);
        payout.setNetMicro(net);
        payoutRepo.save(payout);

        balanceService.credit(bet.getUserId(), net);
        bet.setStatus(Bet.STATUS_WON);
        betRepo.save(bet);
    }

    static BigInteger applyBps(BigInteger amount, int bps) {
        if (bps <= 0 || amount.signum() <= 0) {
            return BigInteger.ZERO;
        }
        return new BigDecimal(amount)
                .multiply(BigDecimal.valueOf(bps))
                .divide(BigDecimal.valueOf(10_000), 0, RoundingMode.DOWN)
                .toBigInteger();
    }

    static BigInteger shareOf(BigInteger stake, BigInteger pool, BigInteger sideTotal) {
        if (stake.signum() <= 0 || pool.signum() <= 0 || sideTotal.signum() <= 0) {
            return BigInteger.ZERO;
        }
        return new BigDecimal(stake)
                .multiply(new BigDecimal(pool))
                .divide(new BigDecimal(sideTotal), 0, RoundingMode.DOWN)
                .toBigInteger();
    }
}
