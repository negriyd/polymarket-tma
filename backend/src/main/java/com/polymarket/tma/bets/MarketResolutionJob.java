package com.polymarket.tma.bets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Polls Gamma and settles parimutuel markets after the grace period. */
@Component
public class MarketResolutionJob {

    private static final Logger log = LoggerFactory.getLogger(MarketResolutionJob.class);

    private final MarketResolutionService resolutionService;

    public MarketResolutionJob(MarketResolutionService resolutionService) {
        this.resolutionService = resolutionService;
    }

    @Scheduled(fixedDelay = 600_000, initialDelay = 60_000)
    public void run() {
        try {
            resolutionService.lockDueMarkets();
            resolutionService.resolveReadyMarkets();
        } catch (Exception e) {
            log.warn("Market resolution job failed: {}", e.toString());
        }
    }
}
