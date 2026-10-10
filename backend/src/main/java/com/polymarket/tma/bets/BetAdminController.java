package com.polymarket.tma.bets;

import com.polymarket.tma.bets.dto.BetDtos;
import com.polymarket.tma.common.ApiException;
import com.polymarket.tma.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/bets")
public class BetAdminController {

    private final AppProperties props;
    private final MarketResolutionService resolutionService;

    public BetAdminController(AppProperties props, MarketResolutionService resolutionService) {
        this.props = props;
        this.resolutionService = resolutionService;
    }

    @PostMapping("/markets/{conditionId}/resolve")
    public BetDtos.AdminMarketResponse resolve(HttpServletRequest http,
                                               @PathVariable String conditionId,
                                               @Valid @RequestBody BetDtos.AdminResolveRequest req) {
        requireAdmin(http);
        resolutionService.resolveMarket(conditionId, (short) req.winningOutcomeIndex(),
                MarketResolutionService.SOURCE_MANUAL);
        return new BetDtos.AdminMarketResponse(conditionId, "RESOLVED", req.winningOutcomeIndex());
    }

    @PostMapping("/markets/{conditionId}/cancel")
    public BetDtos.AdminMarketResponse cancel(HttpServletRequest http, @PathVariable String conditionId) {
        requireAdmin(http);
        resolutionService.cancelMarket(conditionId);
        return new BetDtos.AdminMarketResponse(conditionId, "CANCELLED", null);
    }

    private void requireAdmin(HttpServletRequest http) {
        String expected = props.bets() == null ? null : props.bets().adminSecret();
        if (expected == null || expected.isBlank()) {
            throw ApiException.forbidden("ADMIN_DISABLED", "Admin API not configured (BETS_ADMIN_SECRET)");
        }
        String provided = http.getHeader("X-Admin-Secret");
        if (provided == null || !expected.equals(provided)) {
            throw ApiException.forbidden("ADMIN_FORBIDDEN", "Invalid admin secret");
        }
    }
}
