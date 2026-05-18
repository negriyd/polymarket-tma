package com.polymarket.tma.trading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.polymarket.tma.auth.entity.AppUser;
import com.polymarket.tma.auth.jwt.AuthPrincipal;
import com.polymarket.tma.auth.repo.AppUserRepository;
import com.polymarket.tma.market.entity.FavoriteMarket;
import com.polymarket.tma.market.repo.FavoriteMarketRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class PositionsControllerTest {

    @Mock PositionsClient client;
    @Mock AppUserRepository userRepo;
    @Mock FavoriteMarketRepository favoriteRepo;

    @InjectMocks PositionsController controller;

    @Test
    void decoratesEachPositionWithFavoriteFlag() {
        AppUser user = new AppUser();
        user.setId(7L);
        user.setWalletAddress("0xabc");
        when(userRepo.findById(7L)).thenReturn(Optional.of(user));

        FavoriteMarket favored = new FavoriteMarket();
        favored.setUserId(7L);
        favored.setConditionId("0xCID-A");
        when(favoriteRepo.findAllByUserId(7L)).thenReturn(List.of(favored));

        PositionsClient.Position posA = sample("0xcid-a", "asset-a");
        PositionsClient.Position posB = sample("0xcid-b", "asset-b");
        when(client.getPositions("0xabc")).thenReturn(Mono.just(List.of(posA, posB)));

        AuthPrincipal principal = new AuthPrincipal(7L, null);

        StepVerifier.create(controller.list(principal))
                .assertNext(out -> {
                    assertThat(out).hasSize(2);
                    assertThat(out.get(0).favorite()).isTrue();
                    assertThat(out.get(0).conditionId()).isEqualTo("0xcid-a");
                    assertThat(out.get(1).favorite()).isFalse();
                })
                .verifyComplete();
    }

    @Test
    void rejectsCallerWithoutWallet() {
        AppUser user = new AppUser();
        user.setId(7L);
        // no wallet address — controller must short-circuit instead of calling upstream.
        when(userRepo.findById(7L)).thenReturn(Optional.of(user));
        lenient().when(client.getPositions(any())).thenReturn(Mono.empty());

        AuthPrincipal principal = new AuthPrincipal(7L, null);
        assertThat(catching(() -> controller.list(principal)))
                .hasMessageContaining("Connect wallet");
    }

    private static Throwable catching(Runnable r) {
        try {
            r.run();
            return new AssertionError("Expected exception");
        } catch (Throwable t) {
            return t;
        }
    }

    private static PositionsClient.Position sample(String conditionId, String asset) {
        return new PositionsClient.Position(
                "0xabc", asset, conditionId,
                new BigDecimal("10"), new BigDecimal("0.55"),
                new BigDecimal("5.5"), new BigDecimal("6.0"),
                new BigDecimal("0.5"), new BigDecimal("0.09"),
                new BigDecimal("5.5"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("0.6"), false, false,
                "Will X happen?", "will-x-happen", null,
                "event-x", "Yes", 0, "No", "asset-no",
                "2026-12-31T00:00:00Z", false, null);
    }
}
