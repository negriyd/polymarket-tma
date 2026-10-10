package com.polymarket.tma.bets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.polymarket.tma.auth.entity.AppUser;
import com.polymarket.tma.auth.repo.AppUserRepository;
import com.polymarket.tma.bets.dto.BetDtos;
import com.polymarket.tma.bets.entity.Bet;
import com.polymarket.tma.bets.entity.PlatformMarket;
import com.polymarket.tma.bets.repo.BetRepository;
import com.polymarket.tma.bets.repo.PlatformMarketRepository;
import com.polymarket.tma.common.ApiException;
import com.polymarket.tma.config.AppProperties;
import com.polymarket.tma.market.MarketService;
import com.polymarket.tma.market.dto.MarketDto;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class BetServiceTest {

    private static final String WALLET = "0x496b8D9f22Eaa44FC9266f7da1B4a51C3EE58227";
    private static final String TREASURY = "0x0000000000000000000000000000000000000abc";
    private static final String USDC = "0x2791Bca1f2de4661ED88A30C99A7a9449Aa84174";
    private static final String CONDITION = "0x" + "ab".repeat(32);

    @Mock private AppProperties props;
    @Mock private AppUserRepository userRepo;
    @Mock private BetRepository betRepo;
    @Mock private PlatformMarketRepository marketRepo;
    @Mock private MarketService marketService;
    @Mock private UsdcTransferVerifier transferVerifier;

    private BetService service;

    @BeforeEach
    void setUp() {
        when(props.bets()).thenReturn(new AppProperties.Bets(
                true, TREASURY, "", "", 500, new BigDecimal("1"), new BigDecimal("1"), 15, 24, 5));

        AppUser user = new AppUser();
        user.setWalletAddress(WALLET);
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));

        service = new BetService(props, userRepo, betRepo, marketRepo, marketService, transferVerifier);
    }

    @Test
    void prepareCreatesPendingBetAndReturnsTransferTx() {
        when(props.polygon()).thenReturn(new AppProperties.Polygon(
                "https://polygon-rpc.com", USDC, "", "", ""));
        when(marketService.get(eq(CONDITION))).thenReturn(Mono.just(stubMarket()));
        when(marketRepo.findById(CONDITION)).thenReturn(Optional.empty());
        when(marketRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(betRepo.save(any())).thenAnswer(inv -> {
            Bet b = inv.getArgument(0);
            b.setId(42L);
            return b;
        });

        BetDtos.PrepareBetResponse resp = service.prepare(1L, new BetDtos.PrepareBetRequest(
                CONDITION, 0, new BigDecimal("5"), "idem-1"));

        assertThat(resp.betId()).isEqualTo(42L);
        assertThat(resp.paymentTx().kind()).isEqualTo("BET_USDC_TRANSFER");
        assertThat(resp.paymentTx().to()).isEqualTo(USDC);
        assertThat(resp.outcomeLabel()).isEqualTo("Yes");
        assertThat(resp.platformFeeBps()).isEqualTo(500);

        ArgumentCaptor<Bet> captor = ArgumentCaptor.forClass(Bet.class);
        verify(betRepo).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(Bet.STATUS_PENDING);
        assertThat(captor.getValue().getAmountMicroUsdc()).isEqualTo(BigInteger.valueOf(5_000_000L));
    }

    @Test
    void prepareRejectsNegRiskMarket() {
        MarketDto neg = stubMarket(true);
        when(marketService.get(eq(CONDITION))).thenReturn(Mono.just(neg));

        assertThatThrownBy(() -> service.prepare(1L, new BetDtos.PrepareBetRequest(
                CONDITION, 0, new BigDecimal("5"), null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("negRisk");

        verify(betRepo, never()).save(any());
    }

    @Test
    void confirmMarksBetConfirmedWhenTransferVerified() {
        Bet pending = new Bet();
        pending.setId(7L);
        pending.setUserId(1L);
        pending.setConditionId(CONDITION);
        pending.setOutcomeIndex((short) 0);
        pending.setAmountMicroUsdc(BigInteger.valueOf(5_000_000L));
        pending.setStatus(Bet.STATUS_PENDING);

        PlatformMarket pm = new PlatformMarket();
        pm.setConditionId(CONDITION);
        pm.setStatus(PlatformMarket.STATUS_OPEN);

        when(betRepo.findByIdAndUserId(7L, 1L)).thenReturn(Optional.of(pending));
        when(betRepo.findByPaymentTxHash("0xdead")).thenReturn(Optional.empty());
        when(marketRepo.findById(CONDITION)).thenReturn(Optional.of(pm));
        when(transferVerifier.verify(
                eq("0xdead"), eq(WALLET), eq(TREASURY), eq(BigInteger.valueOf(5_000_000L))))
                .thenReturn(Optional.of(new UsdcTransferVerifier.VerifiedTransfer(
                        "0xdead", WALLET, TREASURY, BigInteger.valueOf(5_000_000L))));
        when(marketService.get(CONDITION)).thenReturn(Mono.just(stubMarket()));
        when(betRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BetDtos.BetResponse resp = service.confirm(1L, new BetDtos.ConfirmBetRequest(7L, "0xdead", null));

        assertThat(resp.status()).isEqualTo(Bet.STATUS_CONFIRMED);
        assertThat(resp.paymentTxHash()).isEqualTo("0xdead");
        assertThat(pending.getConfirmedAt()).isNotNull();
    }

    private static MarketDto stubMarket() {
        return stubMarket(false);
    }

    private static MarketDto stubMarket(boolean negRisk) {
        return new MarketDto(
                "1",
                CONDITION,
                "Test?",
                "slug",
                null,
                null,
                null,
                null,
                Instant.now().plusSeconds(3600),
                null,
                null,
                true,
                false,
                true,
                false,
                null,
                null,
                null,
                List.of("Yes", "No"),
                List.of("1", "2"),
                List.of("0.5", "0.5"),
                negRisk);
    }
}
