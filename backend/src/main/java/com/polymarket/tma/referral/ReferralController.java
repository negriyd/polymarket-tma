package com.polymarket.tma.referral;

import com.polymarket.tma.auth.jwt.AuthPrincipal;
import com.polymarket.tma.common.ApiException;
import com.polymarket.tma.share.TelegramBotClient;
import java.math.BigDecimal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/** {@code GET /api/referrals/me} → invite link and what it has brought so far. */
@RestController
@RequestMapping("/api/referrals")
public class ReferralController {

    private final ReferralService referrals;
    private final TelegramBotClient bot;

    public ReferralController(ReferralService referrals, TelegramBotClient bot) {
        this.referrals = referrals;
        this.bot = bot;
    }

    /** @param earnedUsdc referrer's cut of submitted fee batches, in pUSD */
    public record Me(String code, String link, int invited, String earnedUsdc, int sharePercent, long months,
                     boolean lifetime, boolean partner) {}

    @GetMapping("/me")
    public Mono<Me> me(@AuthenticationPrincipal AuthPrincipal principal) {
        if (principal == null) {
            throw ApiException.unauthorized("UNAUTHENTICATED", "Authentication required");
        }
        ReferralService.Summary s = referrals.summary(principal.userId());
        return bot.username().map(name -> new Me(s.code(), "https://t.me/" + name + "?startapp=r" + s.code(),
                s.invited(), new BigDecimal(s.earnedMicro()).movePointLeft(6).stripTrailingZeros().toPlainString(),
                s.sharePercent(), s.months(), s.lifetime(), s.partner()));
    }
}
