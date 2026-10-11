package com.polymarket.tma.admin;

import com.polymarket.tma.auth.jwt.AuthPrincipal;
import com.polymarket.tma.common.ApiException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <pre>
 *   GET /api/operator/access -> {operator: bool}  (whether to show the stats page)
 *   GET /api/operator/stats  -> platform totals; 403 for everyone but the fee recipient's owner
 * </pre>
 */
@RestController
@RequestMapping("/api/operator")
public class PlatformStatsController {

    private final PlatformStatsService service;

    public PlatformStatsController(PlatformStatsService service) {
        this.service = service;
    }

    public record Access(boolean operator) {}

    @GetMapping("/access")
    public Access access(@AuthenticationPrincipal AuthPrincipal principal) {
        return new Access(service.isOperator(userId(principal)));
    }

    @GetMapping("/stats")
    public PlatformStatsService.Stats stats(@AuthenticationPrincipal AuthPrincipal principal) {
        if (!service.isOperator(userId(principal))) {
            throw ApiException.forbidden("NOT_OPERATOR", "Only the platform operator can see these stats");
        }
        return service.stats();
    }

    private static long userId(AuthPrincipal principal) {
        if (principal == null) {
            throw ApiException.unauthorized("UNAUTHENTICATED", "Authentication required");
        }
        return principal.userId();
    }
}
