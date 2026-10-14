package com.polymarket.tma.predictions;

import com.polymarket.tma.common.ApiException;
import com.polymarket.tma.market.MarketService;
import com.polymarket.tma.market.dto.MarketDto;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * "Прогноз без денег": a free Yes/No pick on an open market, kept until the market resolves. One pick
 * per user and market; it can be changed while the market is still trading.
 */
@Service
public class PredictionService {

    private final JdbcTemplate jdbc;
    private final MarketService markets;

    public PredictionService(JdbcTemplate jdbc, MarketService markets) {
        this.jdbc = jdbc;
        this.markets = markets;
    }

    /**
     * @param correct null while the market is open or awaiting its result
     */
    public record Prediction(String conditionId, String marketId, int outcomeIndex, String outcomeLabel,
                             BigDecimal price, String question, Instant createdAt, Instant resolvedAt,
                             Boolean correct) {}

    public record Counts(long correct, long wrong, long pending) {}

    /**
     * Track record for the profile label; bets count only resolved real positions.
     *
     * @param paperProfit what a $5 bet on every resolved prediction, at the price when it was made,
     *                    would have returned net — the "bet for real" nudge
     */
    public record TrackRecord(Counts predictions, Counts bets, boolean allowsMessages, BigDecimal paperProfit) {}

    public Prediction predict(long userId, String marketKey, String side) {
        MarketDto m = markets.get(marketKey).block(Duration.ofSeconds(5));
        if (m == null || m.conditionId() == null || m.id() == null) {
            throw ApiException.notFound("MARKET_NOT_FOUND", "Market not found: " + marketKey);
        }
        if (Boolean.TRUE.equals(m.closed()) || Boolean.FALSE.equals(m.acceptingOrders())) {
            throw ApiException.conflict("MARKET_NOT_OPEN", "This market no longer takes predictions");
        }
        int idx = outcomeIndex(m.outcomes(), side);
        String label = m.outcomes() != null && idx < m.outcomes().size() ? m.outcomes().get(idx)
                : ("yes".equals(side) ? "Yes" : "No");
        BigDecimal price = null;
        try {
            if (m.outcomePrices() != null && idx < m.outcomePrices().size()) {
                price = new BigDecimal(m.outcomePrices().get(idx));
            }
        } catch (NumberFormatException ignored) {
            // price is informational
        }
        String cid = m.conditionId().toLowerCase(Locale.ROOT);
        int changed = jdbc.update("""
                INSERT INTO prediction (user_id, condition_id, market_id, outcome_index, outcome_label, price, question)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (user_id, condition_id) DO UPDATE
                   SET outcome_index = EXCLUDED.outcome_index, outcome_label = EXCLUDED.outcome_label,
                       price = EXCLUDED.price, updated_at = NOW()
                 WHERE prediction.resolved_at IS NULL
                """, userId, cid, m.id(), idx, label, price, m.question());
        if (changed == 0) {
            throw ApiException.conflict("PREDICTION_RESOLVED", "This prediction already has a result");
        }
        return list(userId, cid).get(0);
    }

    /** Yes = the "Yes" outcome (or the first one), No = "No" (or the second). */
    static int outcomeIndex(List<String> outcomes, String side) {
        if (outcomes != null) {
            for (int i = 0; i < outcomes.size(); i++) {
                if (side.equalsIgnoreCase(outcomes.get(i))) {
                    return i;
                }
            }
        }
        return "yes".equals(side) ? 0 : 1;
    }

    public List<Prediction> list(long userId, String conditionId) {
        String sql = "SELECT condition_id, market_id, outcome_index, outcome_label, price, question, created_at, "
                + "resolved_at, correct FROM prediction WHERE user_id = ?"
                + (conditionId == null ? "" : " AND condition_id = ?")
                + " ORDER BY resolved_at IS NOT NULL, COALESCE(resolved_at, updated_at) DESC LIMIT 200";
        Object[] args = conditionId == null ? new Object[] {userId}
                : new Object[] {userId, conditionId.toLowerCase(Locale.ROOT)};
        return jdbc.query(sql, (rs, i) -> new Prediction(
                rs.getString("condition_id"), rs.getString("market_id"), rs.getInt("outcome_index"),
                rs.getString("outcome_label"), rs.getBigDecimal("price"), rs.getString("question"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("resolved_at") == null ? null : rs.getTimestamp("resolved_at").toInstant(),
                (Boolean) rs.getObject("correct")), args);
    }

    public TrackRecord trackRecord(long userId) {
        Counts predictions = jdbc.queryForObject("""
                SELECT COUNT(*) FILTER (WHERE correct) AS ok, COUNT(*) FILTER (WHERE correct = FALSE) AS bad,
                       COUNT(*) FILTER (WHERE resolved_at IS NULL) AS pending
                FROM prediction WHERE user_id = ?
                """, (rs, i) -> new Counts(rs.getLong("ok"), rs.getLong("bad"), rs.getLong("pending")), userId);
        Counts bets = jdbc.queryForObject("""
                SELECT COUNT(*) FILTER (WHERE won) AS ok, COUNT(*) FILTER (WHERE NOT won) AS bad
                FROM bet_result WHERE user_id = ?
                """, (rs, i) -> new Counts(rs.getLong("ok"), rs.getLong("bad"), 0), userId);
        Boolean allows = jdbc.queryForObject("SELECT allows_messages FROM app_user WHERE id = ?", Boolean.class, userId);
        BigDecimal paper = jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE WHEN correct AND price > 0 THEN 5 / price - 5
                                         WHEN correct = FALSE THEN -5 ELSE 0 END), 0)
                FROM prediction WHERE user_id = ? AND resolved_at IS NOT NULL
                """, BigDecimal.class, userId);
        return new TrackRecord(predictions, bets, Boolean.TRUE.equals(allows),
                paper == null ? BigDecimal.ZERO : paper.setScale(2, java.math.RoundingMode.HALF_UP));
    }

    public void setAllowsMessages(long userId, boolean enabled) {
        jdbc.update("UPDATE app_user SET allows_messages = ? WHERE id = ?", enabled, userId);
    }
}
