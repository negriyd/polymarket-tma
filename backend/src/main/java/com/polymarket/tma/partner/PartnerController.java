package com.polymarket.tma.partner;

import com.polymarket.tma.admin.PlatformStatsService;
import com.polymarket.tma.auth.jwt.AuthPrincipal;
import com.polymarket.tma.common.ApiException;
import com.polymarket.tma.share.TelegramBotClient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * <pre>
 *   GET  /api/operator/partners                         -> partners with their terms and totals (operator only)
 *   POST /api/operator/partners      {user, shareBps, months?, note?, publicName?} -> make a user a partner
 *   PUT  /api/operator/partners/{id} {shareBps, months?, active, note?, publicName?} -> change terms / pause
 *   DELETE /api/operator/partners/{id}              -> remove the role (back to default referral terms)
 *   GET  /api/partner/me                        -> partner cabinet: terms, link, funnel per tag
 * </pre>
 * {@code months} null = lifetime.
 */
@RestController
public class PartnerController {

    private final PartnerService service;
    private final PlatformStatsService platform;
    private final TelegramBotClient bot;

    public PartnerController(PartnerService service, PlatformStatsService platform, TelegramBotClient bot) {
        this.service = service;
        this.platform = platform;
        this.bot = bot;
    }

    public record AddRequest(@NotBlank @Size(max = 80) String user,
                             @NotNull @Min(1) @Max(10_000) Integer shareBps,
                             @Min(1) @Max(1_200) Integer months,
                             @Size(max = 256) String note,
                             @Size(max = 64) String publicName) {}

    public record UpdateRequest(@NotNull @Min(1) @Max(10_000) Integer shareBps,
                                @Min(1) @Max(1_200) Integer months,
                                @NotNull Boolean active,
                                @Size(max = 256) String note,
                                @Size(max = 64) String publicName) {}

    /** @param link invite link without a tag; the cabinet appends {@code _s<tag>} */
    public record PartnerView(PartnerService.Partner partner, String link) {}

    public record CabinetView(PartnerService.Cabinet cabinet, String link) {}

    @GetMapping("/api/operator/partners")
    public Mono<List<PartnerView>> list(@AuthenticationPrincipal AuthPrincipal principal) {
        requireOperator(principal);
        List<PartnerService.Partner> partners = service.list();
        return bot.username().map(name -> partners.stream().map(p -> new PartnerView(p, link(name, p.code()))).toList());
    }

    @PostMapping("/api/operator/partners")
    public Mono<PartnerView> add(@AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody AddRequest req) {
        requireOperator(principal);
        PartnerService.Partner p = service.add(req.user(), req.shareBps(), req.months(), req.note(), req.publicName());
        return bot.username().map(name -> new PartnerView(p, link(name, p.code())));
    }

    @PutMapping("/api/operator/partners/{userId}")
    public Mono<PartnerView> update(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long userId,
                                    @Valid @RequestBody UpdateRequest req) {
        requireOperator(principal);
        PartnerService.Partner p = service.update(userId, req.shareBps(), req.months(), req.active(), req.note(),
                req.publicName());
        return bot.username().map(name -> new PartnerView(p, link(name, p.code())));
    }

    @DeleteMapping("/api/operator/partners/{userId}")
    public void remove(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long userId) {
        requireOperator(principal);
        service.remove(userId);
    }

    @GetMapping("/api/partner/me")
    public Mono<CabinetView> me(@AuthenticationPrincipal AuthPrincipal principal) {
        PartnerService.Cabinet c = service.cabinet(userId(principal));
        return bot.username().map(name -> new CabinetView(c, link(name, c.code())));
    }

    private static String link(String botName, String code) {
        return "https://t.me/" + botName + "?startapp=r" + code;
    }

    private void requireOperator(AuthPrincipal principal) {
        if (!platform.isOperator(userId(principal))) {
            throw ApiException.forbidden("NOT_OPERATOR", "Only the platform operator can manage partners");
        }
    }

    private static long userId(AuthPrincipal principal) {
        if (principal == null) {
            throw ApiException.unauthorized("UNAUTHENTICATED", "Authentication required");
        }
        return principal.userId();
    }
}
