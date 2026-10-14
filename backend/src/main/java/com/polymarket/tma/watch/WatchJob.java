package com.polymarket.tma.watch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Price-move and closing alerts every 10 minutes; the weekly digest and group boards on Monday (UTC). */
@Component
public class WatchJob {

    private static final Logger log = LoggerFactory.getLogger(WatchJob.class);

    private final MarketWatchService watch;
    private final WeeklyDigestService digest;
    private final com.polymarket.tma.groups.GroupService groups;

    public WatchJob(MarketWatchService watch, WeeklyDigestService digest, com.polymarket.tma.groups.GroupService groups) {
        this.watch = watch;
        this.digest = digest;
        this.groups = groups;
    }

    @Scheduled(fixedDelay = 600_000, initialDelay = 150_000)
    public void watch() {
        try {
            watch.run();
        } catch (Exception e) {
            log.warn("Market watch failed: {}", e.toString());
        }
    }

    /** Hourly through Monday so a restart at 10:00 doesn't skip the week; sends once per user per week. */
    @Scheduled(cron = "0 5 10-20 * * MON", zone = "UTC")
    public void weekly() {
        try {
            digest.run();
        } catch (Exception e) {
            log.warn("Weekly digest failed: {}", e.toString());
        }
        try {
            groups.weekly();
        } catch (Exception e) {
            log.warn("Weekly group leaderboards failed: {}", e.toString());
        }
    }
}
