package com.polymarket.tma.admin;

import com.polymarket.tma.auth.repo.AppUserRepository;
import com.polymarket.tma.config.AppProperties;
import com.polymarket.tma.trading.relayer.RelayerClient;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Platform totals for its operator — the user whose trading (deposit) wallet receives the fee.
 * Bets are CLOB orders Polymarket accepted (matched or resting); amounts are pUSD.
 */
@Service
public class PlatformStatsService {

    /** Order statuses Polymarket returned for accepted orders (failed / in-flight ones excluded). */
    private static final String ACCEPTED = "status IN ('matched', 'live', 'delayed', 'unmatched')";

    private final JdbcTemplate jdbc;
    private final AppUserRepository users;
    private final RelayerClient relayer;
    private final AppProperties props;

    public PlatformStatsService(JdbcTemplate jdbc, AppUserRepository users, RelayerClient relayer,
                                AppProperties props) {
        this.jdbc = jdbc;
        this.users = users;
        this.relayer = relayer;
        this.props = props;
    }

    /**
     * @param volumeUsdc   pUSD committed in accepted BUY orders (resting orders may still be cancelled)
     * @param avgBetUsdc   volume / accepted BUY orders
     * @param feesUsdc     platform's part of submitted fee batches
     * @param referralUsdc referrers' part of submitted fee batches
     */
    public record Stats(long users, long usersLast7d, long usersWithWallet, long referredUsers,
                        long bets, String volumeUsdc, String avgBetUsdc,
                        long feePayments, String feesUsdc, String referralUsdc) {}

    public boolean isOperator(long userId) {
        AppProperties.Fees fees = props.fees();
        if (fees == null || fees.recipientAddress() == null || fees.recipientAddress().isBlank()) {
            return false;
        }
        return users.findById(userId)
                .map(u -> u.getWalletAddress())
                .filter(eoa -> eoa != null && !eoa.isBlank())
                .map(relayer::depositWalletAddress)
                .map(wallet -> wallet.equalsIgnoreCase(fees.recipientAddress()))
                .orElse(false);
    }

    public Stats stats() {
        Map<String, Object> u = jdbc.queryForMap("SELECT COUNT(*) AS total, "
                + "COUNT(*) FILTER (WHERE created_at > NOW() - INTERVAL '7 days') AS last7, "
                + "COUNT(*) FILTER (WHERE wallet_address IS NOT NULL) AS wallet, "
                + "COUNT(*) FILTER (WHERE referred_by IS NOT NULL) AS referred FROM app_user");
        Map<String, Object> o = jdbc.queryForMap("SELECT COUNT(*) AS bets, "
                + "COUNT(*) FILTER (WHERE side = 'BUY') AS buys, "
                + "COALESCE(SUM(maker_amount) FILTER (WHERE side = 'BUY'), 0) AS volume "
                + "FROM order_audit WHERE " + ACCEPTED);
        Map<String, Object> f = jdbc.queryForMap("SELECT COUNT(*) AS n, COALESCE(SUM(platform_amount), 0) AS platform, "
                + "COALESCE(SUM(referrer_amount), 0) AS referrer FROM fee_payment");

        BigDecimal volume = micro(o.get("volume"));
        long buys = ((Number) o.get("buys")).longValue();
        BigDecimal avg = buys == 0 ? BigDecimal.ZERO
                : volume.divide(BigDecimal.valueOf(buys), 2, java.math.RoundingMode.HALF_UP);
        return new Stats(
                num(u.get("total")), num(u.get("last7")), num(u.get("wallet")), num(u.get("referred")),
                num(o.get("bets")), usd(volume), usd(avg),
                num(f.get("n")), usd(micro(f.get("platform"))), usd(micro(f.get("referrer"))));
    }

    private static long num(Object v) {
        return v == null ? 0 : ((Number) v).longValue();
    }

    private static BigDecimal micro(Object v) {
        return v == null ? BigDecimal.ZERO : new BigDecimal(v.toString()).movePointLeft(6);
    }

    private static String usd(BigDecimal v) {
        return v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
