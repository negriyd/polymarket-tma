package com.polymarket.tma.partner;

import com.polymarket.tma.auth.entity.AppUser;
import com.polymarket.tma.auth.repo.AppUserRepository;
import com.polymarket.tma.common.ApiException;
import com.polymarket.tma.referral.ReferralService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Partner program: the operator makes a user a partner with individual terms (fee share and term),
 * the partner sees opens, sign-ups, wallets, bets, volume and earnings per link tag.
 * Earnings themselves are paid by the referral split ({@link ReferralService#split}).
 */
@Service
public class PartnerService {

    /** Order statuses Polymarket returned for accepted orders, as in the platform stats. */
    private static final String ACCEPTED = "status IN ('matched', 'live', 'delayed', 'unmatched')";
    private static final Pattern TELEGRAM_ID = Pattern.compile("\\d{3,20}");
    private static final Pattern USERNAME = Pattern.compile("@?([A-Za-z0-9_]{3,64})");

    /**
     * Progress of the users one partner brought, per link tag ({@code source} null = link without a tag).
     *
     * @param launches   app opens through this partner's links (repeat opens included)
     * @param volumeUsdc pUSD in accepted BUY orders of these users
     * @param earnedUsdc partner's part of these users' fee batches
     */
    public record Funnel(String source, long launches, long users, long wallet, long bet,
                         String volumeUsdc, String earnedUsdc) {}

    /** @param months null = lifetime */
    public record Partner(long userId, long telegramId, String username, String name, int shareBps,
                          Integer months, boolean active, String note, Instant since, boolean hasWallet,
                          String code, Funnel totals) {}

    public record Cabinet(int shareBps, Integer months, Instant since, String code, Funnel totals,
                          List<Funnel> sources) {}

    private static final String SOURCES_SQL = """
            SELECT NULLIF(COALESCE(s.source, l.source), '') AS source, COALESCE(l.launches, 0) AS launches,
                   COALESCE(s.users, 0) AS users, COALESCE(s.wallet, 0) AS wallet, COALESCE(s.bet, 0) AS bet,
                   COALESCE(s.volume, 0) AS volume, COALESCE(s.earned, 0) AS earned
            FROM (SELECT COALESCE(u.signup_source, '') AS source, COUNT(*) AS users,
                         COUNT(*) FILTER (WHERE u.wallet_address IS NOT NULL) AS wallet,
                         COUNT(*) FILTER (WHERE EXISTS (SELECT 1 FROM order_audit o
                             WHERE o.user_id = u.id AND o.%1$s)) AS bet,
                         SUM((SELECT COALESCE(SUM(o.maker_amount), 0) FROM order_audit o
                             WHERE o.user_id = u.id AND o.side = 'BUY' AND o.%1$s)) AS volume,
                         SUM((SELECT COALESCE(SUM(r.amount_micro), 0) FROM referral_reward r
                             WHERE r.referee_id = u.id AND r.referrer_id = u.referred_by)) AS earned
                  FROM app_user u WHERE u.referred_by = ? GROUP BY 1) s
            FULL OUTER JOIN (SELECT COALESCE(source, '') AS source, COUNT(*) AS launches FROM app_launch
                             WHERE ref_code = ? GROUP BY 1) l ON l.source = s.source
            ORDER BY users DESC, launches DESC
            """.formatted(ACCEPTED);

    private final PartnerRepository partners;
    private final AppUserRepository users;
    private final ReferralService referrals;
    private final JdbcTemplate jdbc;

    public PartnerService(PartnerRepository partners, AppUserRepository users, ReferralService referrals,
                          JdbcTemplate jdbc) {
        this.partners = partners;
        this.users = users;
        this.referrals = referrals;
        this.jdbc = jdbc;
    }

    public boolean isPartner(long userId) {
        return partners.findActive(userId).isPresent();
    }

    public List<Partner> list() {
        return partners.all().stream().map(this::toPartner).toList();
    }

    /**
     * Makes a user a partner. {@code who} is a Telegram @username or numeric Telegram ID; the user
     * must have opened the app once, so that they have an account and a wallet to be paid to.
     */
    @Transactional
    public Partner add(String who, int shareBps, Integer months, String note) {
        AppUser user = findUser(who);
        if (partners.find(user.getId()).isPresent()) {
            throw ApiException.conflict("PARTNER_EXISTS", "This user is already a partner");
        }
        partners.upsert(user.getId(), shareBps, months, true, blankToNull(note));
        return toPartner(partners.find(user.getId()).orElseThrow());
    }

    @Transactional
    public Partner update(long userId, int shareBps, Integer months, boolean active, String note) {
        if (partners.find(userId).isEmpty()) {
            throw ApiException.notFound("PARTNER_NOT_FOUND", "No such partner");
        }
        partners.upsert(userId, shareBps, months, active, blankToNull(note));
        return toPartner(partners.find(userId).orElseThrow());
    }

    public Cabinet cabinet(long userId) {
        PartnerRepository.Terms terms = partners.findActive(userId)
                .orElseThrow(() -> ApiException.forbidden("NOT_PARTNER", "Only partners have a partner cabinet"));
        String code = referrals.codeOf(userId);
        List<Funnel> sources = sources(userId, code);
        return new Cabinet(terms.shareBps(), terms.months(), terms.createdAt(), code, total(sources), sources);
    }

    private Partner toPartner(PartnerRepository.Terms t) {
        AppUser u = users.findById(t.userId()).orElseThrow();
        String code = referrals.codeOf(u.getId());
        String name = String.join(" ", u.getFirstName() == null ? "" : u.getFirstName(),
                u.getLastName() == null ? "" : u.getLastName()).trim();
        return new Partner(u.getId(), u.getTelegramId(), u.getUsername(), name, t.shareBps(), t.months(),
                t.active(), t.note(), t.createdAt(), u.getWalletAddress() != null && !u.getWalletAddress().isBlank(),
                code, total(sources(u.getId(), code)));
    }

    private List<Funnel> sources(long userId, String code) {
        return jdbc.query(SOURCES_SQL, (rs, i) -> new Funnel(rs.getString("source"), rs.getLong("launches"),
                rs.getLong("users"), rs.getLong("wallet"), rs.getLong("bet"),
                usd(rs.getBigDecimal("volume")), usd(rs.getBigDecimal("earned"))), userId, code);
    }

    static Funnel total(List<Funnel> rows) {
        long launches = 0;
        long users = 0;
        long wallet = 0;
        long bet = 0;
        BigDecimal volume = BigDecimal.ZERO;
        BigDecimal earned = BigDecimal.ZERO;
        for (Funnel f : rows) {
            launches += f.launches();
            users += f.users();
            wallet += f.wallet();
            bet += f.bet();
            volume = volume.add(new BigDecimal(f.volumeUsdc()));
            earned = earned.add(new BigDecimal(f.earnedUsdc()));
        }
        return new Funnel(null, launches, users, wallet, bet, volume.toPlainString(), earned.toPlainString());
    }

    private AppUser findUser(String who) {
        String q = who == null ? "" : who.trim();
        if (q.startsWith("https://t.me/")) {
            q = q.substring("https://t.me/".length());
        }
        if (TELEGRAM_ID.matcher(q).matches()) {
            return users.findByTelegramId(Long.parseLong(q)).orElseThrow(PartnerService::notFound);
        }
        var m = USERNAME.matcher(q);
        if (!m.matches()) {
            throw ApiException.badRequest("BAD_PARTNER_USER", "Enter a Telegram @username or numeric ID");
        }
        List<Long> ids = jdbc.queryForList("SELECT id FROM app_user WHERE LOWER(username) = LOWER(?)",
                Long.class, m.group(1));
        if (ids.isEmpty()) {
            throw notFound();
        }
        return users.findById(ids.get(0)).orElseThrow(PartnerService::notFound);
    }

    private static ApiException notFound() {
        return ApiException.notFound("PARTNER_USER_NOT_FOUND",
                "No such user yet: they need to open the app once before becoming a partner");
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String usd(BigDecimal micro) {
        return (micro == null ? BigDecimal.ZERO : micro).movePointLeft(6).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
