package com.polymarket.tma.referral;

import com.polymarket.tma.auth.entity.AppUser;
import com.polymarket.tma.auth.repo.AppUserRepository;
import com.polymarket.tma.trading.relayer.DepositWalletBatchSubmitted;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Referral program: invite codes, sign-up attribution from {@code startapp} links, the referrer's
 * share of trading fees, and launch tracking per channel tag.
 */
@Service
public class ReferralService {

    private static final Logger log = LoggerFactory.getLogger(ReferralService.class);
    private static final String ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration PENDING_REWARD_TTL = Duration.ofMinutes(15);

    private final AppUserRepository users;
    private final JdbcTemplate jdbc;
    private final RedisTemplate<String, Object> redis;
    private final ReferralProperties props;

    public ReferralService(AppUserRepository users, JdbcTemplate jdbc, RedisTemplate<String, Object> redis,
                           ReferralProperties props) {
        this.users = users;
        this.jdbc = jdbc;
        this.redis = redis;
        this.props = props;
    }

    /** Fee split for one trade: who gets what of the platform fee. */
    public record Split(long referrerId, String referrerEoa, BigInteger referrerAmount) {}

    public record Summary(String code, int invited, BigInteger earnedMicro, int sharePercent, long months) {}

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

    /** The referrer's cut of {@code feeMicro} if this user was invited within the program window. */
    public Optional<Split> split(long refereeId, BigInteger feeMicro) {
        if (props.shareBps() <= 0 || feeMicro == null || feeMicro.signum() <= 0) {
            return Optional.empty();
        }
        AppUser referee = users.findById(refereeId).orElse(null);
        if (referee == null || referee.getReferredBy() == null || referee.getReferredAt() == null
                || referee.getReferredAt().plus(props.duration()).isBefore(Instant.now())) {
            return Optional.empty();
        }
        BigInteger cut = feeMicro.multiply(BigInteger.valueOf(props.shareBps())).divide(BigInteger.valueOf(10_000));
        if (cut.signum() <= 0) {
            return Optional.empty();
        }
        return users.findById(referee.getReferredBy())
                .filter(r -> r.getWalletAddress() != null && !r.getWalletAddress().isBlank())
                .map(r -> new Split(r.getId(), r.getWalletAddress(), cut));
    }

    /** Remembers the referrer's cut of a prepared fee batch until the user submits it. */
    public void rememberPendingReward(String batchHash, long refereeId, Split split) {
        try {
            redis.opsForValue().set(pendingKey(batchHash), Map.of(
                    "referrerId", split.referrerId(),
                    "refereeId", refereeId,
                    "amount", split.referrerAmount().toString()), PENDING_REWARD_TTL);
        } catch (RuntimeException e) {
            log.warn("Could not remember referral reward for batch {}: {}", batchHash, e.toString());
        }
    }

    /** A submitted fee batch that carried a referrer's cut becomes a recorded reward. */
    @EventListener
    public void onBatchSubmitted(DepositWalletBatchSubmitted event) {
        if (!"TRADING_FEE".equals(event.purpose())) {
            return;
        }
        try {
            Object raw = redis.opsForValue().get(pendingKey(event.batchHash()));
            if (!(raw instanceof Map<?, ?> m)) {
                return;
            }
            redis.delete(pendingKey(event.batchHash()));
            jdbc.update("INSERT INTO referral_reward (referrer_id, referee_id, amount_micro, relayer_tx_id) "
                            + "VALUES (?, ?, ?, ?)",
                    ((Number) m.get("referrerId")).longValue(), ((Number) m.get("refereeId")).longValue(),
                    new java.math.BigDecimal(String.valueOf(m.get("amount"))), event.relayerTxId());
        } catch (RuntimeException e) {
            log.warn("Could not record referral reward for batch {}: {}", event.batchHash(), e.toString());
        }
    }

    public Summary summary(long userId) {
        String code = codeOf(userId);
        Integer invited = jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE referred_by = ?",
                Integer.class, userId);
        java.math.BigDecimal earned = jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount_micro), 0) FROM referral_reward WHERE referrer_id = ?",
                java.math.BigDecimal.class, userId);
        return new Summary(code, invited == null ? 0 : invited,
                earned == null ? BigInteger.ZERO : earned.toBigInteger(),
                props.shareBps() / 100, props.duration().toDays() / 30);
    }

    private static String pendingKey(String batchHash) {
        return "pm:refreward:" + batchHash;
    }

    private static String randomCode() {
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
