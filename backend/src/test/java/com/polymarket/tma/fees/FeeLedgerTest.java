package com.polymarket.tma.fees;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.polymarket.tma.referral.ReferralService;
import com.polymarket.tma.trading.relayer.DepositWalletBatchSubmitted;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;

class FeeLedgerTest {

    @SuppressWarnings("unchecked")
    private final RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, Object> values = mock(ValueOperations.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final FeeLedger ledger = new FeeLedger(redis, jdbc);

    @Test
    void booksFeeAndReferrerCutWhenTheBatchIsSubmitted() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("pm:feebatch:0xb")).thenReturn(Map.of(
                "payerId", 2, "platform", "75000", "referrerId", 1, "referrer", "25000"));

        ledger.onBatchSubmitted(new DepositWalletBatchSubmitted(2, "TRADING_FEE", "0xb", "tx1"));

        verify(jdbc).update(eq("INSERT INTO fee_payment (user_id, platform_amount, referrer_id, referrer_amount, "
                        + "relayer_tx_id) VALUES (?, ?, ?, ?, ?)"),
                eq(2L), eq(new BigDecimal("75000")), eq(1L), eq(new BigDecimal("25000")), eq("tx1"));
        verify(jdbc).update(eq("INSERT INTO referral_reward (referrer_id, referee_id, amount_micro, relayer_tx_id) "
                + "VALUES (?, ?, ?, ?)"), eq(1L), eq(2L), eq(new BigDecimal("25000")), eq("tx1"));
    }

    @Test
    void ignoresOtherBatchesAndUnknownOnes() {
        ledger.onBatchSubmitted(new DepositWalletBatchSubmitted(2, "REDEEM", "0xb", "tx1"));
        when(redis.opsForValue()).thenReturn(values);
        ledger.onBatchSubmitted(new DepositWalletBatchSubmitted(2, "TRADING_FEE", "0xunknown", "tx2"));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void remembersBreakdownWithoutReferrer() {
        when(redis.opsForValue()).thenReturn(values);
        ledger.rememberPending("0xb", 2, java.math.BigInteger.valueOf(100_000), (ReferralService.Split) null);
        verify(values).set(eq("pm:feebatch:0xb"), eq(Map.of("payerId", 2L, "platform", "100000")), any());
    }
}
