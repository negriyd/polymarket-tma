package com.polymarket.tma.trading;

import com.polymarket.tma.auth.entity.AppUser;
import com.polymarket.tma.auth.jwt.AuthPrincipal;
import com.polymarket.tma.auth.repo.AppUserRepository;
import com.polymarket.tma.common.ApiException;
import com.polymarket.tma.market.repo.FavoriteMarketRepository;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api")
public class PositionsController {

    private final PositionsClient positions;
    private final AppUserRepository userRepo;
    private final FavoriteMarketRepository favorites;

    public PositionsController(PositionsClient positions,
                               AppUserRepository userRepo,
                               FavoriteMarketRepository favorites) {
        this.positions = positions;
        this.userRepo = userRepo;
        this.favorites = favorites;
    }

    /**
     * Returns positions for the caller's wallet, decorated with a {@code favorite} flag derived from
     * the local {@code favorite_market} table. Tagging is best-effort: a database read happens once per
     * request before the upstream Mono runs, so the join is consistent with the favorites the user can
     * see in the UI without an extra round-trip on the client.
     */
    @GetMapping("/positions")
    public Mono<List<PositionsClient.Position>> list(@AuthenticationPrincipal AuthPrincipal principal) {
        AppUser u = requireWallet(principal);
        Set<String> favored = favorites.findAllByUserId(u.getId()).stream()
                .map(f -> f.getConditionId() == null ? "" : f.getConditionId().toLowerCase())
                .collect(Collectors.toUnmodifiableSet());
        return positions.getPositions(u.getWalletAddress())
                .map(list -> list.stream()
                        .map(p -> p.withFavorite(p.conditionId() != null
                                && favored.contains(p.conditionId().toLowerCase())))
                        .toList());
    }

    @DeleteMapping("/orders/{orderId}")
    public Mono<Void> cancel(@AuthenticationPrincipal AuthPrincipal principal,
                             @PathVariable String orderId) {
        requireWallet(principal);
        // Placeholder: real implementation calls CLOB DELETE /order with L2 auth headers.
        // Until L1/L2 are wired, return a structured error so the UI can show a banner.
        return Mono.error(ApiException.upstream("CLOB_NOT_WIRED",
                "Cancel is not wired in MVP scaffolding (see docs/trading.md)"));
    }

    private AppUser requireWallet(AuthPrincipal principal) {
        if (principal == null) {
            throw ApiException.unauthorized("UNAUTHENTICATED", "Authentication required");
        }
        AppUser u = userRepo.findById(principal.userId())
                .orElseThrow(() -> ApiException.unauthorized("USER_GONE", "User not found"));
        if (u.getWalletAddress() == null || u.getWalletAddress().isBlank()) {
            throw ApiException.badRequest("WALLET_REQUIRED", "Connect wallet and save address first");
        }
        return u;
    }
}
