package com.polymarket.tma.bets;

import com.polymarket.tma.auth.jwt.AuthPrincipal;
import com.polymarket.tma.bets.dto.BetDtos;
import com.polymarket.tma.common.ApiException;
import com.polymarket.tma.market.MarketService;
import com.polymarket.tma.market.dto.MarketDto;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bets")
public class BetController {

    private final BetService bets;
    private final MarketPoolService poolService;
    private final MarketService marketService;
    private final WithdrawService withdrawService;

    public BetController(BetService bets,
                         MarketPoolService poolService,
                         MarketService marketService,
                         WithdrawService withdrawService) {
        this.bets = bets;
        this.poolService = poolService;
        this.marketService = marketService;
        this.withdrawService = withdrawService;
    }

    /** Public config for UI (fee bps, min bet, enabled flag). */
    @GetMapping("/config")
    public BetDtos.ConfigResponse config() {
        return bets.config();
    }

    @GetMapping("/pool/{conditionId}")
    public BetDtos.PoolResponse pool(@PathVariable String conditionId) {
        List<String> labels = List.of("YES", "NO");
        try {
            MarketDto market = marketService.get(conditionId).block(Duration.ofSeconds(3));
            if (market.outcomes() != null && market.outcomes().size() >= 2) {
                labels = market.outcomes();
            }
        } catch (RuntimeException ignored) {
            // Fall back to YES/NO labels when Gamma is slow or market unknown.
        }
        return poolService.pool(conditionId, labels);
    }

    @PostMapping("/prepare")
    public BetDtos.PrepareBetResponse prepare(@AuthenticationPrincipal AuthPrincipal principal,
                                              @Valid @RequestBody BetDtos.PrepareBetRequest req) {
        requireAuth(principal);
        return bets.prepare(principal.userId(), req);
    }

    @PostMapping("/confirm")
    public BetDtos.BetResponse confirm(@AuthenticationPrincipal AuthPrincipal principal,
                                       @Valid @RequestBody BetDtos.ConfirmBetRequest req) {
        requireAuth(principal);
        return bets.confirm(principal.userId(), req);
    }

    @GetMapping("/mine")
    public List<BetDtos.BetResponse> mine(@AuthenticationPrincipal AuthPrincipal principal) {
        requireAuth(principal);
        return bets.mine(principal.userId());
    }

    @GetMapping("/balance")
    public BetDtos.BalanceResponse balance(@AuthenticationPrincipal AuthPrincipal principal) {
        requireAuth(principal);
        return withdrawService.balance(principal.userId());
    }

    @PostMapping("/withdraw")
    public BetDtos.WithdrawalResponse withdraw(@AuthenticationPrincipal AuthPrincipal principal,
                                               @Valid @RequestBody BetDtos.WithdrawRequest req) {
        requireAuth(principal);
        return withdrawService.withdraw(principal.userId(), req);
    }

    @GetMapping("/withdrawals")
    public List<BetDtos.WithdrawalResponse> withdrawals(@AuthenticationPrincipal AuthPrincipal principal) {
        requireAuth(principal);
        return withdrawService.history(principal.userId());
    }

    private static void requireAuth(AuthPrincipal principal) {
        if (principal == null) {
            throw ApiException.unauthorized("UNAUTHENTICATED", "Authentication required");
        }
    }
}
