package com.polymarket.tma.share;

import com.polymarket.tma.auth.jwt.AuthPrincipal;
import com.polymarket.tma.common.ApiException;
import com.polymarket.tma.market.i18n.MarketLocale;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/** {@code POST /api/share/market} → deep link + prepared Telegram message for a market card. */
@RestController
@RequestMapping("/api/share")
public class ShareController {

    private final ShareService service;

    public ShareController(ShareService service) {
        this.service = service;
    }

    @PostMapping("/market")
    public Mono<ShareDtos.MarketShare> market(@AuthenticationPrincipal AuthPrincipal principal,
                                             @Valid @RequestBody ShareDtos.MarketShareRequest req,
                                             @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false)
                                             String language) {
        if (principal == null) {
            throw ApiException.unauthorized("UNAUTHENTICATED", "Authentication required");
        }
        return service.shareMarket(principal.userId(), req, MarketLocale.fromHeader(language));
    }
}
