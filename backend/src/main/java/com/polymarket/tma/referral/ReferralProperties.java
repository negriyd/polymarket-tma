package com.polymarket.tma.referral;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param shareBps share of the platform trading fee paid to the referrer (2500 = 25% of the fee)
 * @param months   how long after the invited user's sign-up the referrer earns, in calendar months;
 *                 applies to everyone invited so far, since it is checked on every trade
 */
@ConfigurationProperties(prefix = "app.referrals")
public record ReferralProperties(int shareBps, int months) {
}
