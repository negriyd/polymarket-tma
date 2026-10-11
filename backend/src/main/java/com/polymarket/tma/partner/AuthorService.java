package com.polymarket.tma.partner;

import com.polymarket.tma.auth.entity.AppUser;
import com.polymarket.tma.auth.repo.AppUserRepository;
import com.polymarket.tma.common.ApiException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * What a partner's audience sees: the partner as an "author" with a public forecast accuracy (their
 * own free predictions) and the weekly "Top forecaster" leaderboard among users who came through the
 * partner's links. Only active partners are shown.
 */
@Service
public class AuthorService {

    /** Resolved predictions an author needs before the accuracy is shown publicly. */
    public static final int MIN_RESOLVED_AUTHOR = 5;
    /** Resolved predictions in the week a user needs to enter the leaderboard (no "1 of 1" winners). */
    public static final int MIN_RESOLVED_WEEK = 3;
    private static final int TOP = 10;

    /** @param pct null until {@link #MIN_RESOLVED_AUTHOR} predictions resolved */
    public record Accuracy(long correct, long resolved, Integer pct) {}

    /** @param pick the author's free prediction on the requested market, if any */
    public record Author(String code, String name, String username, Accuracy accuracy, Pick pick) {}

    public record Pick(String outcome, Double price, boolean resolved, Boolean correct) {}

    public record Row(int rank, String name, long correct, long resolved, int pct, boolean me) {}

    /**
     * @param weekStart inclusive, Monday 00:00 UTC
     * @param me        the viewer's row when outside the top (null otherwise or without enough predictions)
     */
    public record Leaderboard(String code, String author, Instant weekStart, Instant weekEnd, int minResolved,
                              int participants, List<Row> top, Row me) {}

    private final JdbcTemplate jdbc;
    private final AppUserRepository users;
    private final PartnerRepository partners;

    public AuthorService(JdbcTemplate jdbc, AppUserRepository users, PartnerRepository partners) {
        this.jdbc = jdbc;
        this.users = users;
        this.partners = partners;
    }

    /**
     * The author for this viewer: the partner whose link they opened ({@code code}), or else the
     * partner who invited them. Empty when neither is an active partner (a regular user's share
     * link carries their invite code too).
     */
    public Optional<Author> forViewer(long viewerId, String code, String conditionId) {
        Optional<Author> byLink = code == null || code.isBlank() ? Optional.empty()
                : users.findByRefCode(code.trim().toLowerCase()).flatMap(p -> author(p, conditionId));
        return byLink.or(() -> users.findById(viewerId).map(AppUser::getReferredBy).flatMap(users::findById)
                .flatMap(p -> author(p, conditionId)));
    }

    /** Public view of an active partner, or empty. */
    public Optional<Author> author(AppUser partner, String conditionId) {
        return partners.findActive(partner.getId()).map(terms -> new Author(partner.getRefCode(),
                displayName(partner, terms), partner.getUsername(), accuracy(partner.getId()),
                conditionId == null || conditionId.isBlank() ? null : pick(partner.getId(), conditionId)));
    }

    /** Accuracy of an active partner's own predictions, or empty for everyone else. */
    public Optional<Accuracy> publicAccuracy(long userId) {
        return partners.findActive(userId).map(t -> accuracy(userId))
                .filter(a -> a.pct() != null);
    }

    public Accuracy accuracy(long userId) {
        var row = jdbc.queryForMap("SELECT COUNT(*) FILTER (WHERE correct) AS correct, "
                + "COUNT(*) FILTER (WHERE resolved_at IS NOT NULL) AS resolved FROM prediction WHERE user_id = ?", userId);
        long correct = ((Number) row.get("correct")).longValue();
        long resolved = ((Number) row.get("resolved")).longValue();
        Integer pct = resolved >= MIN_RESOLVED_AUTHOR ? (int) Math.round(100.0 * correct / resolved) : null;
        return new Accuracy(correct, resolved, pct);
    }

    private Pick pick(long userId, String conditionId) {
        return jdbc.query("SELECT outcome_label, price, resolved_at, correct FROM prediction "
                        + "WHERE user_id = ? AND LOWER(condition_id) = LOWER(?)",
                (rs, i) -> {
                    java.math.BigDecimal price = rs.getBigDecimal("price");
                    boolean resolved = rs.getTimestamp("resolved_at") != null;
                    Boolean correct = rs.getObject("correct") == null ? null : rs.getBoolean("correct");
                    return new Pick(rs.getString("outcome_label"), price == null ? null : price.doubleValue(),
                            resolved, correct);
                }, userId, conditionId).stream().findFirst().orElse(null);
    }

    /** Weekly leaderboard of the partner's audience; {@code previous} = the week before the current one. */
    public Leaderboard leaderboard(String code, boolean previous, long viewerId) {
        AppUser partner = users.findByRefCode(code == null ? "" : code.trim().toLowerCase())
                .orElseThrow(AuthorService::noAuthor);
        PartnerRepository.Terms terms = partners.findActive(partner.getId()).orElseThrow(AuthorService::noAuthor);
        Instant start = weekStart(Instant.now());
        if (previous) {
            start = start.minusSeconds(7 * 24 * 3600);
        }
        Instant end = start.plusSeconds(7 * 24 * 3600);
        List<Row> rows = ranked(partner.getId(), partner.getRefCode(), start, end, viewerId);
        List<Row> top = rows.stream().limit(TOP).toList();
        Row me = rows.stream().filter(Row::me).filter(r -> r.rank() > TOP).findFirst().orElse(null);
        return new Leaderboard(partner.getRefCode(), displayName(partner, terms), start, end, MIN_RESOLVED_WEEK,
                rows.size(), top, me);
    }

    /** Audience: invited by the partner or opened one of their links; the partner is not ranked. */
    private static final String RANKED_SQL = """
            SELECT p.user_id, u.username, u.first_name, u.last_name,
                   COUNT(*) FILTER (WHERE p.correct) AS correct, COUNT(*) AS resolved
            FROM prediction p JOIN app_user u ON u.id = p.user_id
            WHERE p.resolved_at >= ? AND p.resolved_at < ? AND u.id <> ?
              AND (u.referred_by = ? OR EXISTS (SELECT 1 FROM app_launch l WHERE l.user_id = u.id AND l.ref_code = ?))
            GROUP BY p.user_id, u.username, u.first_name, u.last_name
            HAVING COUNT(*) >= ?
            ORDER BY correct DESC, COUNT(*) - COUNT(*) FILTER (WHERE p.correct) ASC, MIN(p.resolved_at) ASC
            """;

    private List<Row> ranked(long partnerId, String code, Instant start, Instant end, long viewerId) {
        List<Row> rows = new ArrayList<>();
        jdbc.query(RANKED_SQL, rs -> {
            long correct = rs.getLong("correct");
            long resolved = rs.getLong("resolved");
            AppUser u = new AppUser();
            u.setUsername(rs.getString("username"));
            u.setFirstName(rs.getString("first_name"));
            u.setLastName(rs.getString("last_name"));
            rows.add(new Row(rows.size() + 1, publicUserName(u), correct, resolved,
                    (int) Math.round(100.0 * correct / resolved), rs.getLong("user_id") == viewerId));
        }, java.sql.Timestamp.from(start), java.sql.Timestamp.from(end), partnerId, partnerId, code, MIN_RESOLVED_WEEK);
        return rows;
    }

    static Instant weekStart(Instant now) {
        LocalDate monday = now.atOffset(ZoneOffset.UTC).toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return monday.atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    static String displayName(AppUser partner, PartnerRepository.Terms terms) {
        if (terms.publicName() != null && !terms.publicName().isBlank()) {
            return terms.publicName();
        }
        return publicUserName(partner);
    }

    /** "@username", or "First L." for users without one. */
    static String publicUserName(AppUser u) {
        if (u.getUsername() != null && !u.getUsername().isBlank()) {
            return "@" + u.getUsername();
        }
        String first = u.getFirstName() == null ? "" : u.getFirstName().trim();
        String last = u.getLastName() == null || u.getLastName().isBlank() ? "" : " " + u.getLastName().trim().charAt(0) + ".";
        String name = (first + last).trim();
        return name.isEmpty() ? "—" : name;
    }

    private static ApiException noAuthor() {
        return ApiException.notFound("AUTHOR_NOT_FOUND", "No such partner");
    }
}
