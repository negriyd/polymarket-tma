package com.polymarket.tma.referral;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param shareBps share of the platform trading fee paid to the referrer (2500 = 25% of the fee)
 * @param duration how long after the invited user's sign-up the referrer earns
 */
@ConfigurationProperties(prefix = "app.referrals")
public record ReferralProperties(int shareBps, Duration duration) {
}
