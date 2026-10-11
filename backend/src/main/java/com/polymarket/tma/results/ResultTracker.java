package com.polymarket.tma.results;

import com.polymarket.tma.market.client.GammaClient;
import com.polymarket.tma.market.dto.MarketDto;
import com.polymarket.tma.trading.PositionsClient;
import com.polymarket.tma.trading.relayer.RelayerClient;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Finds markets that resolved and records what that means for users: free predictions become
 * correct / wrong, real positions become won / lost bets. Each result queues a notification
 * (predictions always, real bets only when won).
 */
@Service
public class ResultTracker {

    private static final Logger log = LoggerFactory.getLogger(ResultTracker.class);
    private static final int BATCH = 40;
    private static final String ACCEPTED = "status IN ('matched', 'live', 'delayed', 'unmatched')";

    private final JdbcTemplate jdbc;
    private final GammaClient gamma;
    private final PositionsClient positions;
    private final RelayerClient relayer;

    public ResultTracker(JdbcTemplate jdbc, GammaClient gamma, PositionsClient positions, RelayerClient relayer) {
        this.jdbc = jdbc;
        this.gamma = gamma;
        this.positions = positions;
        this.relayer = relayer;
    }

    /** A resolved market: the outcome whose price settled at 1. */
    record Resolution(MarketDto market, int winnerIndex) {
        String outcome(int index) {
            List<String> o = market.outcomes();
            return o != null && index < o.size() ? o.get(index) : (index == 0 ? "Yes" : "No");
        }
    }

    static Integer winnerIndex(MarketDto m) {
        if (!Boolean.TRUE.equals(m.closed()) || m.outcomePrices() == null) {
            return null;
        }
        for (int i = 0; i < m.outcomePrices().size(); i++) {
            try {
                if (Double.parseDouble(m.outcomePrices().get(i)) >= 0.99) {
                    return i;
                }
            } catch (NumberFormatException ignored) {
                // not a price
            }
        }
        return null;
    }

    /** Resolved markets among {@code conditionIds} (lower-case keys); unresolved ones are absent. */
    Map<String, Resolution> resolutions(List<String> conditionIds) {
        Map<String, Resolution> out = new HashMap<>();
        for (int i = 0; i < conditionIds.size(); i += BATCH) {
            List<String> batch = conditionIds.subList(i, Math.min(i + BATCH, conditionIds.size()));
            List<MarketDto> closed = gamma.marketsByConditionIds(batch, null, true).block(Duration.ofSeconds(20));
            if (closed == null) {
                continue;
            }
            for (MarketDto m : closed) {
                Integer w = winnerIndex(m);
                if (w != null && m.conditionId() != null) {
                    out.put(m.conditionId().toLowerCase(Locale.ROOT), new Resolution(m, w));
                }
            }
        }
        return out;
    }

    public int resolvePredictions() {
        List<String> open = jdbc.queryForList(
                "SELECT DISTINCT condition_id FROM prediction WHERE resolved_at IS NULL LIMIT 2000", String.class);
        if (open.isEmpty()) {
            return 0;
        }
        int n = 0;
        for (Map.Entry<String, Resolution> e : resolutions(open).entrySet()) {
            Resolution r = e.getValue();
            List<Map<String, Object>> rows = jdbc.queryForList("""
                    UPDATE prediction SET resolved_at = NOW(), correct = (outcome_index = ?)
                     WHERE condition_id = ? AND resolved_at IS NULL
                    RETURNING user_id, correct, outcome_label, market_id, question
                    """, r.winnerIndex(), e.getKey());
            for (Map<String, Object> row : rows) {
                boolean correct = Boolean.TRUE.equals(row.get("correct"));
                queue(((Number) row.get("user_id")).longValue(), correct ? "PREDICTION_CORRECT" : "PREDICTION_WRONG",
                        e.getKey(), (String) row.get("market_id"), (String) row.get("question"),
                        correct ? (String) row.get("outcome_label") : r.outcome(r.winnerIndex()), null);
                n++;
            }
        }
        if (n > 0) {
            log.info("Resolved {} predictions", n);
        }
        return n;
    }

    /**
     * Real bets: positions of users who placed accepted orders, read from Polymarket's data API
     * (positions stay listed until redeemed, so a market resolving between runs is still seen).
     */
    public int resolveBets() {
        List<Map<String, Object>> traders = jdbc.queryForList("SELECT DISTINCT u.id, u.wallet_address FROM app_user u "
                + "JOIN order_audit o ON o.user_id = u.id WHERE o." + ACCEPTED + " AND u.wallet_address IS NOT NULL");
        int n = 0;
        for (Map<String, Object> t : traders) {
            long userId = ((Number) t.get("id")).longValue();
            try {
                n += resolveBetsOf(userId, (String) t.get("wallet_address"));
            } catch (RuntimeException e) {
                log.warn("Bet results for user {} failed: {}", userId, e.toString());
            }
        }
        if (n > 0) {
            log.info("Recorded {} bet results", n);
        }
        return n;
    }

    private int resolveBetsOf(long userId, String eoa) {
        List<PositionsClient.Position> list = positions.getPositions(relayer.depositWalletAddress(eoa))
                .block(Duration.ofSeconds(20));
        if (list == null || list.isEmpty()) {
            return 0;
        }
        List<String> cids = list.stream().map(PositionsClient.Position::conditionId)
                .filter(c -> c != null && !c.isBlank()).map(c -> c.toLowerCase(Locale.ROOT)).distinct().toList();
        Map<String, Resolution> resolved = resolutions(new ArrayList<>(cids));
        int n = 0;
        for (PositionsClient.Position p : list) {
            if (p.conditionId() == null || p.size() == null || p.size().signum() <= 0) {
                continue;
            }
            String cid = p.conditionId().toLowerCase(Locale.ROOT);
            Resolution r = resolved.get(cid);
            if (r == null) {
                continue;
            }
            boolean won = p.outcomeIndex() == r.winnerIndex();
            BigDecimal payout = won ? p.size() : BigDecimal.ZERO;
            int inserted = jdbc.update("INSERT INTO bet_result (user_id, condition_id, outcome_index, won, payout_usdc) "
                    + "VALUES (?, ?, ?, ?, ?) ON CONFLICT DO NOTHING", userId, cid, p.outcomeIndex(), won, payout);
            if (inserted == 1) {
                n++;
                if (won) {
                    queue(userId, "BET_WON", cid, r.market().id(), r.market().question(), r.outcome(p.outcomeIndex()),
                            payout);
                }
            }
        }
        return n;
    }

    private void queue(long userId, String kind, String conditionId, String marketId, String question,
                       String outcome, BigDecimal payout) {
        jdbc.update("INSERT INTO result_notification (user_id, kind, condition_id, market_id, question, outcome_label, "
                        + "payout_usdc) VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING",
                userId, kind, conditionId, marketId, question == null ? "" : question, outcome, payout);
    }
}
