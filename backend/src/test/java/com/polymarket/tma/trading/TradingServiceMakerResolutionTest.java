package com.polymarket.tma.trading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.polymarket.tma.auth.entity.AppUser;
import com.polymarket.tma.auth.repo.AppUserRepository;
import com.polymarket.tma.common.ApiException;
import com.polymarket.tma.fees.FeeService;
import com.polymarket.tma.market.MarketService;
import com.polymarket.tma.market.dto.MarketDto;
import com.polymarket.tma.trading.OrderBuilder.BuiltOrder;
import com.polymarket.tma.trading.clob.ClobCredentialsStore;
import com.polymarket.tma.trading.dto.OrderDtos;
import com.polymarket.tma.trading.repo.OrderAuditRepository;
import reactor.core.publisher.Mono;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Verifies maker / signer wiring in {@link TradingService#prepare}: EOA copies the wallet to both
 * fields, while proxy / Safe sigs require a distinct {@code makerAddress} from the request.
 */
@ExtendWith(MockitoExtension.class)
class TradingServiceMakerResolutionTest {

    private static final String WALLET = "0x496b8D9f22Eaa44FC9266f7da1B4a51C3EE58227";
    private static final String PROXY = "0x0000000000000000000000000000000000000abc";

    @Mock private OrderBuilder builder;
    @Mock private PendingOrderCache pending;
    @Mock private AppUserRepository userRepo;
    @Mock private OrderAuditRepository auditRepo;
    @Mock private ClobOrderClient clob;
    @Mock private ClobCredentialsStore credentialsStore;
    @Mock private FeeService fees;
    @Mock private MarketService marketService;

    private TradingService service;

    @BeforeEach
    void setUp() {
        AppUser u = new AppUser();
        u.setWalletAddress(WALLET);
        when(userRepo.findById(1L)).thenReturn(Optional.of(u));

        BuiltOrder built = new BuiltOrder(
                "0x" + "0".repeat(64),
                java.math.BigInteger.ZERO,
                java.math.BigInteger.ZERO,
                stubTypedData(),
                new LinkedHashMap<>());
        lenient().when(builder.build(any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(built);
        // Default: regular (non-negRisk) markets unless overridden in a test.
        lenient().when(marketService.get(any())).thenReturn(Mono.just(stubMarket(false)));

        service = new TradingService(builder, pending, userRepo, auditRepo, clob, credentialsStore, fees, marketService);
    }

    @Test
    void eoaSetsMakerAndSignerToWallet() {
        OrderDtos.PrepareOrderRequest req = req(OrderDtos.SignatureType.EOA, null);

        service.prepare(1L, req);

        ArgumentCaptor<String> maker = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> signer = ArgumentCaptor.forClass(String.class);
        verify(builder).build(maker.capture(), signer.capture(), eq(req), eq(false));
        assertThat(maker.getValue()).isEqualTo(WALLET);
        assertThat(signer.getValue()).isEqualTo(WALLET);
    }

    @Test
    void polyProxyUsesProvidedMakerDistinctFromSigner() {
        OrderDtos.PrepareOrderRequest req = req(OrderDtos.SignatureType.POLY_PROXY, PROXY);

        service.prepare(1L, req);

        ArgumentCaptor<String> maker = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> signer = ArgumentCaptor.forClass(String.class);
        verify(builder).build(maker.capture(), signer.capture(), eq(req), eq(false));
        assertThat(maker.getValue()).isEqualTo(PROXY);
        assertThat(signer.getValue()).isEqualTo(WALLET);
    }

    @Test
    void polyProxyRejectsMissingMakerAddress() {
        OrderDtos.PrepareOrderRequest req = req(OrderDtos.SignatureType.POLY_PROXY, null);

        assertThatThrownBy(() -> service.prepare(1L, req))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("makerAddress");

        verify(builder, never()).build(any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void polyProxyRejectsMakerEqualToSigner() {
        OrderDtos.PrepareOrderRequest req = req(OrderDtos.SignatureType.POLY_PROXY, WALLET);

        assertThatThrownBy(() -> service.prepare(1L, req))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("differ");

        verify(builder, never()).build(any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void negRiskMarketPropagatesFlagToOrderBuilder() {
        OrderDtos.PrepareOrderRequest req = req(OrderDtos.SignatureType.EOA, null);
        when(marketService.get(eq("0xcid"))).thenReturn(Mono.just(stubMarket(true)));

        service.prepare(1L, req);

        verify(builder).build(eq(WALLET), eq(WALLET), eq(req), eq(true));
    }

    @Test
    void marketLookupFailureDefaultsToRegularExchange() {
        OrderDtos.PrepareOrderRequest req = req(OrderDtos.SignatureType.EOA, null);
        when(marketService.get(eq("0xcid")))
                .thenReturn(Mono.error(new RuntimeException("gamma down")));

        service.prepare(1L, req);

        // Default is `false` so the user gets a clear CLOB error if the market really was negRisk
        // rather than the prepare endpoint crashing.
        verify(builder).build(eq(WALLET), eq(WALLET), eq(req), eq(false));
    }

    private static OrderDtos.PrepareOrderRequest req(OrderDtos.SignatureType type, String makerAddress) {
        return new OrderDtos.PrepareOrderRequest(
                "0xcid",
                "1",
                OrderDtos.Side.BUY,
                new BigDecimal("0.5"),
                new BigDecimal("1"),
                null,
                type,
                null,
                makerAddress);
    }

    private static Map<String, Object> stubTypedData() {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("tokenId", "1");
        message.put("side", "0");
        Map<String, Object> td = new LinkedHashMap<>();
        td.put("message", message);
        return td;
    }

    private static MarketDto stubMarket(boolean negRisk) {
        return new MarketDto(
                "0",                  // id
                "0xcid",              // conditionId
                "Q?", "slug", null, null, null, null,
                null, null,           // endDate, gameStartTime
                null,                 // marketSlug
                Boolean.TRUE, Boolean.FALSE, Boolean.TRUE, Boolean.FALSE,
                null, null, null,
                java.util.List.of("YES", "NO"),
                java.util.List.of("1", "2"),
                java.util.List.of("0.5", "0.5"),
                negRisk);
    }
}
