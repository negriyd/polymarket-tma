package com.polymarket.tma.trading.bridge;

import com.polymarket.tma.auth.jwt.AuthPrincipal;
import com.polymarket.tma.common.ApiException;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <pre>
 *   GET /api/wallet/deposit-wallet/funding           -> bridge addresses + supported stablecoins
 *   GET /api/wallet/deposit-wallet/funding/deposits  -> recent bridge transfers (poll while waiting)
 * </pre>
 */
@RestController
@RequestMapping("/api/wallet/deposit-wallet/funding")
public class FundingController {

    private final FundingService service;

    public FundingController(FundingService service) {
        this.service = service;
    }

    @GetMapping
    public FundingDtos.Funding funding(@AuthenticationPrincipal AuthPrincipal principal) {
        return service.funding(userId(principal));
    }

    @GetMapping("/deposits")
    public List<FundingDtos.Deposit> deposits(@AuthenticationPrincipal AuthPrincipal principal) {
        return service.deposits(userId(principal));
    }

    private static long userId(AuthPrincipal principal) {
        if (principal == null) {
            throw ApiException.unauthorized("UNAUTHENTICATED", "Authentication required");
        }
        return principal.userId();
    }
}
