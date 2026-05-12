package com.polymarket.tma.market;

import com.polymarket.tma.auth.jwt.AuthPrincipal;
import com.polymarket.tma.common.ApiException;
import com.polymarket.tma.market.entity.FavoriteMarket;
import com.polymarket.tma.market.repo.FavoriteMarketRepository;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/favorites")
public class FavoritesController {

    private final FavoriteMarketRepository repo;

    public FavoritesController(FavoriteMarketRepository repo) {
        this.repo = repo;
    }

    public record FavoriteDto(Long id, String conditionId) {}
    public record AddFavoriteRequest(@NotBlank String conditionId) {}

    @GetMapping
    public List<FavoriteDto> list(@AuthenticationPrincipal AuthPrincipal principal) {
        requireAuth(principal);
        return repo.findAllByUserId(principal.userId()).stream()
                .map(f -> new FavoriteDto(f.getId(), f.getConditionId()))
                .toList();
    }

    @PostMapping
    public FavoriteDto add(@AuthenticationPrincipal AuthPrincipal principal,
                           @RequestBody AddFavoriteRequest req) {
        requireAuth(principal);
        FavoriteMarket f = repo.findByUserIdAndConditionId(principal.userId(), req.conditionId())
                .orElseGet(() -> {
                    FavoriteMarket nf = new FavoriteMarket();
                    nf.setUserId(principal.userId());
                    nf.setConditionId(req.conditionId());
                    return repo.save(nf);
                });
        return new FavoriteDto(f.getId(), f.getConditionId());
    }

    @DeleteMapping("/{conditionId}")
    public void remove(@AuthenticationPrincipal AuthPrincipal principal,
                       @PathVariable String conditionId) {
        requireAuth(principal);
        repo.deleteByUserIdAndConditionId(principal.userId(), conditionId);
    }

    private static void requireAuth(AuthPrincipal principal) {
        if (principal == null) {
            throw ApiException.unauthorized("UNAUTHENTICATED", "Authentication required");
        }
    }
}
