package com.polymarket.tma.bets;

import com.polymarket.tma.bets.entity.PlatformMarket;
import com.polymarket.tma.bets.repo.PlatformMarketRepository;
import com.polymarket.tma.common.ApiException;
import com.polymarket.tma.config.AppProperties;
import com.polymarket.tma.market.MarketService;
import com.polymarket.tma.market.dto.MarketDto;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MarketResolutionService {

    private static final Logger log = LoggerFactory.getLogger(MarketResolutionService.class);

    public static final String SOURCE_GAMMA = "GAMMA";
    public static final String SOURCE_MANUAL = "MANUAL";

    private final AppProperties props;
    private final PlatformMarketRepository marketRepo;
    private final MarketService marketService;
    private final GammaOutcomeResolver outcomeResolver;
    private final PayoutCalculator payoutCalculator;

    public MarketResolutionService(AppProperties props,
                                   PlatformMarketRepository marketRepo,
                                   MarketService marketService,
                                   GammaOutcomeResolver outcomeResolver,
                                   PayoutCalculator payoutCalculator) {
        this.props = props;
        this.marketRepo = marketRepo;
        this.marketService = marketService;
        this.outcomeResolver = outcomeResolver;
        this.payoutCalculator = payoutCalculator;
    }

    /** Lock markets that are within {@code lock-before-end-minutes} of {@code endDate}. */
    @Transactional
    public int lockDueMarkets() {
        if (props.bets() == null || !props.bets().enabled()) {
            return 0;
        }
        int lockMinutes = props.bets().lockBeforeEndMinutesOrDefault();
        Instant now = Instant.now();
        int locked = 0;
        List<PlatformMarket> open = marketRepo.findByStatusIn(List.of(PlatformMarket.STATUS_OPEN));
        for (PlatformMarket pm : open) {
            if (pm.getEndDate() == null) {
                continue;
            }
            Instant lockAt = pm.getEndDate().minus(Duration.ofMinutes(lockMinutes));
            if (!now.isBefore(lockAt)) {
                pm.setStatus(PlatformMarket.STATUS_LOCKED);
                marketRepo.save(pm);
                locked++;
            }
        }
        if (locked > 0) {
            log.info("Locked {} platform markets for new bets", locked);
        }
        return locked;
    }

    /** Attempt Gamma auto-resolve for markets past endDate + grace period. */
    public int resolveReadyMarkets() {
        if (props.bets() == null || !props.bets().enabled()) {
            return 0;
        }
        int graceHours = props.bets().resolutionGraceHoursOrDefault();
        Instant now = Instant.now();
        int resolved = 0;
        for (PlatformMarket pm : marketRepo.findActiveMarketsWithConfirmedBets()) {
            if (pm.getEndDate() == null) {
                continue;
            }
            Instant resolveAfter = pm.getEndDate().plus(Duration.ofHours(graceHours));
            if (now.isBefore(resolveAfter)) {
                continue;
            }
            try {
                MarketDto gamma = marketService.get(pm.getConditionId()).block(Duration.ofSeconds(5));
                var winner = outcomeResolver.winningOutcome(gamma);
                if (winner.isPresent()) {
                    resolveMarket(pm.getConditionId(), winner.get().shortValue(), SOURCE_GAMMA);
                    resolved++;
                } else {
                    log.debug("Market {} past grace but Gamma has no winner yet", pm.getConditionId());
                }
            } catch (RuntimeException e) {
                log.warn("Failed to resolve market {} from Gamma: {}", pm.getConditionId(), e.toString());
            }
        }
        if (resolved > 0) {
            log.info("Auto-resolved {} markets from Gamma", resolved);
        }
        return resolved;
    }

    @Transactional
    public void resolveMarket(String conditionId, short winningOutcomeIndex, String source) {
        if (winningOutcomeIndex != 0 && winningOutcomeIndex != 1) {
            throw ApiException.badRequest("INVALID_OUTCOME", "Winning outcome must be 0 or 1");
        }
        PlatformMarket pm = marketRepo.findById(conditionId)
                .orElseThrow(() -> ApiException.notFound("MARKET_NOT_FOUND", "Platform market not found"));
        if (PlatformMarket.STATUS_RESOLVED.equals(pm.getStatus())
                || PlatformMarket.STATUS_CANCELLED.equals(pm.getStatus())) {
            return;
        }
        pm.setStatus(PlatformMarket.STATUS_RESOLVED);
        pm.setWinningOutcomeIndex(winningOutcomeIndex);
        pm.setResolvedAt(Instant.now());
        pm.setResolutionSource(source);
        marketRepo.save(pm);

        payoutCalculator.settleMarket(conditionId, winningOutcomeIndex);
        log.info("Resolved market {} winner={} source={}", conditionId, winningOutcomeIndex, source);
    }

    @Transactional
    public void cancelMarket(String conditionId) {
        PlatformMarket pm = marketRepo.findById(conditionId)
                .orElseThrow(() -> ApiException.notFound("MARKET_NOT_FOUND", "Platform market not found"));
        if (PlatformMarket.STATUS_CANCELLED.equals(pm.getStatus())) {
            return;
        }
        pm.setStatus(PlatformMarket.STATUS_CANCELLED);
        pm.setResolvedAt(Instant.now());
        pm.setResolutionSource(SOURCE_MANUAL);
        marketRepo.save(pm);

        payoutCalculator.refundMarket(conditionId);
        log.info("Cancelled market {} — refunded confirmed bets", conditionId);
    }
}
