package com.polymarket.tma.referral;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.polymarket.tma.auth.entity.AppUser;
import com.polymarket.tma.auth.repo.AppUserRepository;
import com.polymarket.tma.partner.PartnerRepository;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReferralTest {

    @Test
    void parsesAllLinkShapes() {
        assertThat(StartParam.parse("m_559652_y_rk3f9a2qx"))
                .isEqualTo(new StartParam("559652", "yes", "k3f9a2qx", null));
        assertThat(StartParam.parse("rk3f9a2qx_stg1")).isEqualTo(new StartParam(null, null, "k3f9a2qx", "tg1"));
        assertThat(StartParam.parse("stiktok-may")).isEqualTo(new StartParam(null, null, null, "tiktok-may"));
        assertThat(StartParam.parse("m_42_n")).isEqualTo(new StartParam("42", "no", null, null));
        assertThat(StartParam.parse("m_abc")).isEqualTo(StartParam.NONE);
        assertThat(StartParam.parse("w_snotify")).as("wallet link from a result message")
                .isEqualTo(new StartParam(null, null, null, "notify"));
        assertThat(StartParam.parse(null)).isEqualTo(StartParam.NONE);
    }

    private static AppUser user(long id, Long referredBy, Instant referredAt, String wallet) {
        AppUser u = new AppUser();
        u.setId(id);
        u.setReferredBy(referredBy);
        u.setReferredAt(referredAt);
        u.setWalletAddress(wallet);
        return u;
    }

    private static ReferralService service(AppUserRepository users) {
        return service(users, mock(PartnerRepository.class));
    }

    private static ReferralService service(AppUserRepository users, PartnerRepository partners) {
        return new ReferralService(users, null, new ReferralProperties(2500, Duration.ofDays(365)), partners);
    }

    @Test
    void referrerGetsAQuarterOfTheFeeWithinTheWindow() {
        AppUserRepository users = mock(AppUserRepository.class);
        when(users.findById(2L)).thenReturn(Optional.of(user(2, 1L, Instant.now().minus(Duration.ofDays(30)), "0xb")));
        when(users.findById(1L)).thenReturn(Optional.of(user(1, null, null, "0xReferrerEoa")));

        var split = service(users).split(2L, BigInteger.valueOf(100_000)).orElseThrow();
        assertThat(split.referrerAmount()).isEqualTo(BigInteger.valueOf(25_000));
        assertThat(split.referrerEoa()).isEqualTo("0xReferrerEoa");
    }

    @Test
    void noShareAfterTwelveMonthsOrWithoutReferrerWallet() {
        AppUserRepository users = mock(AppUserRepository.class);
        when(users.findById(2L)).thenReturn(Optional.of(user(2, 1L, Instant.now().minus(Duration.ofDays(400)), "0xb")));
        assertThat(service(users).split(2L, BigInteger.valueOf(100_000))).isEmpty();

        when(users.findById(3L)).thenReturn(Optional.of(user(3, 1L, Instant.now(), "0xc")));
        when(users.findById(1L)).thenReturn(Optional.of(user(1, null, null, null)));
        assertThat(service(users).split(3L, BigInteger.valueOf(100_000))).isEmpty();
    }

    @Test
    void partnerTermsReplaceTheDefaultOnes() {
        AppUserRepository users = mock(AppUserRepository.class);
        PartnerRepository partners = mock(PartnerRepository.class);
        when(users.findById(1L)).thenReturn(Optional.of(user(1, null, null, "0xPartner")));
        when(users.findById(2L)).thenReturn(Optional.of(user(2, 1L, Instant.now().minus(Duration.ofDays(400)), "0xb")));
        when(partners.findActive(1L)).thenReturn(Optional.of(
                new PartnerRepository.Terms(1, 5000, 24, true, null, null, Instant.now())));

        var split = service(users, partners).split(2L, BigInteger.valueOf(100_000)).orElseThrow();
        assertThat(split.referrerAmount()).as("50%, still within 24 months").isEqualTo(BigInteger.valueOf(50_000));

        when(users.findById(3L)).thenReturn(Optional.of(user(3, 1L, Instant.now().minus(Duration.ofDays(800)), "0xc")));
        assertThat(service(users, partners).split(3L, BigInteger.valueOf(100_000))).as("past 24 months").isEmpty();
    }

    @Test
    void lifetimePartnerEarnsForever() {
        AppUserRepository users = mock(AppUserRepository.class);
        PartnerRepository partners = mock(PartnerRepository.class);
        when(users.findById(1L)).thenReturn(Optional.of(user(1, null, null, "0xPartner")));
        when(users.findById(2L)).thenReturn(Optional.of(user(2, 1L, Instant.now().minus(Duration.ofDays(5000)), "0xb")));
        when(partners.findActive(1L)).thenReturn(Optional.of(
                new PartnerRepository.Terms(1, 4000, null, true, null, null, Instant.now())));

        var split = service(users, partners).split(2L, BigInteger.valueOf(100_000)).orElseThrow();
        assertThat(split.referrerAmount()).isEqualTo(BigInteger.valueOf(40_000));
    }
}
