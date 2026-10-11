package com.polymarket.tma.admin;

import com.polymarket.tma.auth.repo.AppUserRepository;
import com.polymarket.tma.config.AppProperties;
import com.polymarket.tma.trading.relayer.RelayerClient;
import java.math.BigDecimal;
import java.util.List;
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
                        long feePayments, String feesUsdc, String referralUsdc,
                        Funnel funnel, List<SourceRow> sources, Predictions predictions) {}

    /**
     * @param accuracyPct correct / resolved, 0 when nothing resolved yet
     * @param messagesSent result messages delivered (a digest counts once)
     */
    public record Predictions(long users, long total, long resolved, long accuracyPct, long messagesSent) {}

    /** Users at each onboarding step (cumulative: each step is a subset of the previous one). */
    public record Funnel(long opened, long predicted, long wallet, long tried, long bet) {}

    /**
     * One channel tag ({@code startapp=s<tag>}), or "direct" for users without one.
     *
     * @param launches app opens through links with this tag in the last 30 days
     */
    public record SourceRow(String source, long launches, long users, long wallet, long bet) {}

    private static final String FUNNEL_SQL = "SELECT COUNT(*) AS opened, "
            + "COUNT(*) FILTER (WHERE EXISTS (SELECT 1 FROM prediction p WHERE p.user_id = u.id)) AS predicted, "
            + "COUNT(*) FILTER (WHERE u.wallet_address IS NOT NULL) AS wallet, "
            + "COUNT(*) FILTER (WHERE EXISTS (SELECT 1 FROM order_audit o WHERE o.user_id = u.id)) AS tried, "
            + "COUNT(*) FILTER (WHERE EXISTS (SELECT 1 FROM order_audit o WHERE o.user_id = u.id AND o."
            + ACCEPTED + ")) AS bet FROM app_user u";

    /** Sign-ups and their progress per channel tag, joined with recent launches through that tag. */
    private static final String SOURCES_SQL = """
            SELECT COALESCE(s.source, l.source, 'direct') AS source,
                   COALESCE(l.launches, 0) AS launches,
                   COALESCE(s.users, 0) AS users, COALESCE(s.wallet, 0) AS wallet, COALESCE(s.bet, 0) AS bet
            FROM (SELECT u.signup_source AS source, COUNT(*) AS users,
                         COUNT(*) FILTER (WHERE u.wallet_address IS NOT NULL) AS wallet,
                         COUNT(*) FILTER (WHERE EXISTS (SELECT 1 FROM order_audit o
                             WHERE o.user_id = u.id AND o.%s)) AS bet
                  FROM app_user u GROUP BY u.signup_source) s
            FULL OUTER JOIN (SELECT source, COUNT(*) AS launches FROM app_launch
                             WHERE created_at > NOW() - INTERVAL '30 days' AND source IS NOT NULL
                             GROUP BY source) l ON l.source = s.source
            ORDER BY users DESC, launches DESC
            LIMIT 30
            """.formatted(ACCEPTED);

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

        Map<String, Object> fn = jdbc.queryForMap(FUNNEL_SQL);
        List<SourceRow> sources = jdbc.query(SOURCES_SQL, (rs, i) -> new SourceRow(rs.getString("source"),
                rs.getLong("launches"), rs.getLong("users"), rs.getLong("wallet"), rs.getLong("bet")));

        BigDecimal volume = micro(o.get("volume"));
        long buys = ((Number) o.get("buys")).longValue();
        BigDecimal avg = buys == 0 ? BigDecimal.ZERO
                : volume.divide(BigDecimal.valueOf(buys), 2, java.math.RoundingMode.HALF_UP);
        return new Stats(
                num(u.get("total")), num(u.get("last7")), num(u.get("wallet")), num(u.get("referred")),
                num(o.get("bets")), usd(volume), usd(avg),
                num(f.get("n")), usd(micro(f.get("platform"))), usd(micro(f.get("referrer"))),
                new Funnel(num(fn.get("opened")), num(fn.get("predicted")), num(fn.get("wallet")),
                        num(fn.get("tried")), num(fn.get("bet"))),
                sources, predictions());
    }

    private Predictions predictions() {
        Map<String, Object> p = jdbc.queryForMap("SELECT COUNT(DISTINCT user_id) AS users, COUNT(*) AS total, "
                + "COUNT(*) FILTER (WHERE resolved_at IS NOT NULL) AS resolved, "
                + "COUNT(*) FILTER (WHERE correct) AS correct FROM prediction");
        Long sent = jdbc.queryForObject("SELECT COUNT(*) FILTER (WHERE status = 'SENT') "
                + "+ COUNT(DISTINCT (user_id, sent_at)) FILTER (WHERE status = 'DIGEST') FROM result_notification",
                Long.class);
        long resolved = num(p.get("resolved"));
        return new Predictions(num(p.get("users")), num(p.get("total")), resolved,
                resolved == 0 ? 0 : Math.round(100.0 * num(p.get("correct")) / resolved), sent == null ? 0 : sent);
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
