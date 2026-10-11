package com.polymarket.tma.partner;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Rows of {@code partner}: individual referral terms the operator agreed with a channel owner. */
@Repository
public class PartnerRepository {

    /**
     * @param shareBps partner's share of the platform fee (5000 = 50%)
     * @param months   how long after each invited user's sign-up the partner earns; null = lifetime
     */
    public record Terms(long userId, int shareBps, Integer months, boolean active, String note, Instant createdAt) {}

    private final JdbcTemplate jdbc;

    public PartnerRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Terms> find(long userId) {
        return jdbc.query("SELECT * FROM partner WHERE user_id = ?", PartnerRepository::map, userId)
                .stream().findFirst();
    }

    /** Terms that apply now: only for an active partner. */
    public Optional<Terms> findActive(long userId) {
        return find(userId).filter(Terms::active);
    }

    public List<Terms> all() {
        return jdbc.query("SELECT * FROM partner ORDER BY active DESC, created_at DESC", PartnerRepository::map);
    }

    public void upsert(long userId, int shareBps, Integer months, boolean active, String note) {
        jdbc.update("""
                INSERT INTO partner (user_id, share_bps, months, active, note) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (user_id) DO UPDATE SET share_bps = EXCLUDED.share_bps, months = EXCLUDED.months,
                    active = EXCLUDED.active, note = EXCLUDED.note, updated_at = NOW()
                """, userId, shareBps, months, active, note);
    }

    private static Terms map(ResultSet rs, int i) throws SQLException {
        int months = rs.getInt("months");
        return new Terms(rs.getLong("user_id"), rs.getInt("share_bps"), rs.wasNull() ? null : months,
                rs.getBoolean("active"), rs.getString("note"), rs.getTimestamp("created_at").toInstant());
    }
}
