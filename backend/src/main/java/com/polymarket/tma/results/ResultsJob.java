package com.polymarket.tma.results;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Every 10 minutes: record results of resolved markets, then send the queued messages. */
@Component
public class ResultsJob {

    private static final Logger log = LoggerFactory.getLogger(ResultsJob.class);

    private final ResultTracker tracker;
    private final ResultNotifier notifier;

    public ResultsJob(ResultTracker tracker, ResultNotifier notifier) {
        this.tracker = tracker;
        this.notifier = notifier;
    }

    @Scheduled(fixedDelay = 600_000, initialDelay = 90_000)
    public void run() {
        try {
            tracker.resolvePredictions();
        } catch (Exception e) {
            log.warn("Prediction results failed: {}", e.toString());
        }
        try {
            tracker.resolveBets();
        } catch (Exception e) {
            log.warn("Bet results failed: {}", e.toString());
        }
        try {
            notifier.sendPending();
        } catch (Exception e) {
            log.warn("Result messages failed: {}", e.toString());
        }
    }
}
