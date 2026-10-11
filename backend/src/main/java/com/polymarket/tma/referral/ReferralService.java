package com.polymarket.tma.referral;

import com.polymarket.tma.auth.entity.AppUser;
import com.polymarket.tma.auth.repo.AppUserRepository;
import com.polymarket.tma.partner.PartnerRepository;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Referral program: invite codes, sign-up attribution from {@code startapp} links, the referrer's
 * share of trading fees, and launch tracking per channel tag. Fee batches are booked by {@code FeeLedger}.
 */
@Service
public class ReferralService {

    private static final Logger log = LoggerFactory.getLogger(ReferralService.class);
    private static final String ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AppUserRepository users;
    private final JdbcTemplate jdbc;
    private final ReferralProperties props;
    private final PartnerRepository partners;

    public ReferralService(AppUserRepository users, JdbcTemplate jdbc, ReferralProperties props,
                           PartnerRepository partners) {
        this.users = users;
        this.jdbc = jdbc;
        this.props = props;
        this.partners = partners;
    }

    /** Fee split for one trade: who gets what of the platform fee. */
    public record Split(long referrerId, String referrerEoa, BigInteger referrerAmount) {}

    /** @param months 0 with {@code lifetime} */
    public record Summary(String code, int invited, BigInteger earnedMicro, int sharePercent, long months,
                          boolean lifetime, boolean partner) {}

    /**
     * Records a Telegram launch and, for a brand-new user, who invited them and through which
     * channel. {@code startParam} comes from validated initData, so it can't be forged by the client.
     */
    @Transactional
    public void onLaunch(AppUser user, boolean newUser, String rawStartParam) {
        StartParam sp = StartParam.parse(rawStartParam);
        if (newUser) {
            if (sp.source() != null) {
                user.setSignupSource(sp.source());
            }
            if (sp.refCode() != null) {
                users.findByRefCode(sp.refCode())
                        .filter(referrer -> !referrer.getId().equals(user.getId()))
                        .ifPresent(referrer -> {
                            user.setReferredBy(referrer.getId());
                            user.setReferredAt(Instant.now());
                            log.info("User {} signed up via referrer {}", user.getId(), referrer.getId());
                        });
            }
            users.save(user);
        }
        if (rawStartParam != null && !rawStartParam.isBlank()) {
            jdbc.update("INSERT INTO app_launch (user_id, new_user, source, ref_code, market_id, start_param) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    user.getId(), newUser, sp.source(), sp.refCode(), sp.marketId(),
                    rawStartParam.length() > 64 ? rawStartParam.substring(0, 64) : rawStartParam);
        }
    }

    /** This user's invite code, created on first use. */
    @Transactional
    public String codeOf(long userId) {
        AppUser user = users.findById(userId).orElseThrow();
        if (user.getRefCode() == null) {
            String code;
            do {
                code = randomCode();
            } while (users.findByRefCode(code).isPresent());
            user.setRefCode(code);
            users.save(user);
        }
        return user.getRefCode();
    }

    /**
     * The referrer's cut of {@code feeMicro} if this user was invited within the program window.
     * An active partner's individual share and term replace the default ones.
     */
    public Optional<Split> split(long refereeId, BigInteger feeMicro) {
        if (feeMicro == null || feeMicro.signum() <= 0) {
            return Optional.empty();
        }
        AppUser referee = users.findById(refereeId).orElse(null);
        if (referee == null || referee.getReferredBy() == null || referee.getReferredAt() == null) {
            return Optional.empty();
        }
        AppUser referrer = users.findById(referee.getReferredBy())
                .filter(r -> r.getWalletAddress() != null && !r.getWalletAddress().isBlank())
                .orElse(null);
        if (referrer == null) {
            return Optional.empty();
        }
        Optional<PartnerRepository.Terms> partner = partners.findActive(referrer.getId());
        int shareBps = partner.map(PartnerRepository.Terms::shareBps).orElse(props.shareBps());
        Instant until = partner.isPresent()
                ? (partner.get().months() == null ? null
                        : referee.getReferredAt().atOffset(ZoneOffset.UTC).plusMonths(partner.get().months()).toInstant())
                : referee.getReferredAt().atOffset(ZoneOffset.UTC).plusMonths(props.months()).toInstant();
        if (shareBps <= 0 || (until != null && until.isBefore(Instant.now()))) {
            return Optional.empty();
        }
        BigInteger cut = feeMicro.multiply(BigInteger.valueOf(shareBps)).divide(BigInteger.valueOf(10_000));
        if (cut.signum() <= 0) {
            return Optional.empty();
        }
        return Optional.of(new Split(referrer.getId(), referrer.getWalletAddress(), cut));
    }

    public Summary summary(long userId) {
        String code = codeOf(userId);
        Integer invited = jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE referred_by = ?",
                Integer.class, userId);
        java.math.BigDecimal earned = jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount_micro), 0) FROM referral_reward WHERE referrer_id = ?",
                java.math.BigDecimal.class, userId);
        Optional<PartnerRepository.Terms> partner = partners.findActive(userId);
        int shareBps = partner.map(PartnerRepository.Terms::shareBps).orElse(props.shareBps());
        Integer months = partner.isPresent() ? partner.get().months() : Integer.valueOf(props.months());
        return new Summary(code, invited == null ? 0 : invited,
                earned == null ? BigInteger.ZERO : earned.toBigInteger(),
                shareBps / 100, months == null ? 0 : months, months == null, partner.isPresent());
    }

    private static String randomCode() {
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
