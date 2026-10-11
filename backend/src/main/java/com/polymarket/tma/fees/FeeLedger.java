package com.polymarket.tma.fees;

import com.polymarket.tma.referral.ReferralService;
import com.polymarket.tma.trading.relayer.DepositWalletBatchSubmitted;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Books platform fees and referrer cuts. A fee batch is prepared before the user signs it, so its
 * breakdown waits in Redis and is written to {@code fee_payment} / {@code referral_reward} only once
 * the relayer accepts the signed batch.
 */
@Service
public class FeeLedger {

    private static final Logger log = LoggerFactory.getLogger(FeeLedger.class);
    private static final Duration PENDING_TTL = Duration.ofMinutes(15);

    private final RedisTemplate<String, Object> redis;
    private final JdbcTemplate jdbc;

    public FeeLedger(RedisTemplate<String, Object> redis, JdbcTemplate jdbc) {
        this.redis = redis;
        this.jdbc = jdbc;
    }

    /** Remembers what a prepared fee batch pays, until the user submits it. */
    public void rememberPending(String batchHash, long payerId, BigInteger platformAmount,
                                ReferralService.Split split) {
        Map<String, Object> v = new HashMap<>();
        v.put("payerId", payerId);
        v.put("platform", platformAmount.toString());
        if (split != null) {
            v.put("referrerId", split.referrerId());
            v.put("referrer", split.referrerAmount().toString());
        }
        try {
            redis.opsForValue().set(key(batchHash), v, PENDING_TTL);
        } catch (RuntimeException e) {
            log.warn("Could not remember fee batch {}: {}", batchHash, e.toString());
        }
    }

    @EventListener
    public void onBatchSubmitted(DepositWalletBatchSubmitted event) {
        if (!"TRADING_FEE".equals(event.purpose())) {
            return;
        }
        try {
            Object raw = redis.opsForValue().get(key(event.batchHash()));
            if (!(raw instanceof Map<?, ?> m)) {
                log.warn("Fee batch {} submitted but its breakdown expired; not booked", event.batchHash());
                return;
            }
            redis.delete(key(event.batchHash()));
            long payer = ((Number) m.get("payerId")).longValue();
            Long referrer = m.get("referrerId") == null ? null : ((Number) m.get("referrerId")).longValue();
            BigDecimal referrerAmount = m.get("referrer") == null
                    ? BigDecimal.ZERO : new BigDecimal(String.valueOf(m.get("referrer")));
            jdbc.update("INSERT INTO fee_payment (user_id, platform_amount, referrer_id, referrer_amount, "
                            + "relayer_tx_id) VALUES (?, ?, ?, ?, ?)",
                    payer, new BigDecimal(String.valueOf(m.get("platform"))), referrer, referrerAmount,
                    event.relayerTxId());
            if (referrer != null && referrerAmount.signum() > 0) {
                jdbc.update("INSERT INTO referral_reward (referrer_id, referee_id, amount_micro, relayer_tx_id) "
                        + "VALUES (?, ?, ?, ?)", referrer, payer, referrerAmount, event.relayerTxId());
            }
        } catch (RuntimeException e) {
            log.warn("Could not book fee batch {}: {}", event.batchHash(), e.toString());
        }
    }

    private static String key(String batchHash) {
        return "pm:feebatch:" + batchHash;
    }
}
