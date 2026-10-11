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
import com.polymarket.tma.trading.relayer.DepositWalletService;
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
    @Mock private com.polymarket.tma.referral.ReferralService referrals;
    @Mock private com.polymarket.tma.fees.FeeLedger feeLedger;
    @Mock private DepositWalletService depositWallets;

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

        service = new TradingService(builder, pending, userRepo, auditRepo, clob, credentialsStore, fees, marketService, depositWallets, referrals, feeLedger);
    }

    @Test
    void eoaSetsMakerAndSignerToWallet() {
        OrderDtos.PrepareOrderRequest req = req(OrderDtos.SignatureType.EOA, null);

        service.prepare(1L, req);

        ArgumentCaptor<String> maker = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> signer = ArgumentCaptor.forClass(String.class);
        verify(builder).build(maker.capture(), signer.capture(), sameOrder(req), eq(false));
        assertThat(maker.getValue()).isEqualTo(WALLET);
        assertThat(signer.getValue()).isEqualTo(WALLET);
    }

    @Test
    void polyProxyUsesProvidedMakerDistinctFromSigner() {
        OrderDtos.PrepareOrderRequest req = req(OrderDtos.SignatureType.POLY_PROXY, PROXY);

        service.prepare(1L, req);

        ArgumentCaptor<String> maker = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> signer = ArgumentCaptor.forClass(String.class);
        verify(builder).build(maker.capture(), signer.capture(), sameOrder(req), eq(false));
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
    void poly1271DerivesDepositWalletAsMaker() {
        String depositWallet = "0x0000000000000000000000000000000000000dec";
        when(depositWallets.depositWalletOf(WALLET)).thenReturn(depositWallet);
        OrderDtos.PrepareOrderRequest req = req(OrderDtos.SignatureType.POLY_1271, null);

        service.prepare(1L, req);

        verify(builder).build(eq(depositWallet), eq(WALLET), sameOrder(req), eq(false));
    }

    @Test
    void noFeeWhenTheMakerIsTheFeeRecipient() {
        String depositWallet = "0x5757DA608Cb6Feaa78dB6388506894F59621eB77";
        when(depositWallets.depositWalletOf(WALLET)).thenReturn(depositWallet);
        when(fees.quote(any())).thenReturn(new com.polymarket.tma.fees.FeeService.FeeQuote(
                java.math.BigInteger.valueOf(20_000), 100, depositWallet.toLowerCase(), null));

        var resp = service.prepare(1L, req(OrderDtos.SignatureType.POLY_1271, null));

        assertThat(resp.feeBatch()).isNull();
        assertThat(resp.feeAmountUsdc()).isNull();
        verify(depositWallets, never()).prepareCollateralTransfers(any(Long.class), any(), any());
    }

    @Test
    void sellingAPositionCarriesNoPlatformFee() {
        when(depositWallets.depositWalletOf(WALLET)).thenReturn("0x0000000000000000000000000000000000000dec");
        var buy = req(OrderDtos.SignatureType.POLY_1271, null);
        var sell = new OrderDtos.PrepareOrderRequest(buy.conditionId(), buy.tokenId(), OrderDtos.Side.SELL,
                buy.price(), buy.size(), buy.orderType(), buy.signatureType(), buy.expiration(), buy.makerAddress());

        var resp = service.prepare(1L, sell);

        assertThat(resp.feeBatch()).isNull();
        assertThat(resp.feeAmountUsdc()).isNull();
        verify(fees, never()).quote(any());
        verify(depositWallets, never()).prepareCollateralTransfers(any(Long.class), any(), any());
    }

    @Test
    void invitedUsersFeeIsSplitWithTheReferrerInOneBatch() {
        String depositWallet = "0x0000000000000000000000000000000000000dec";
        String platform = "0x5757DA608Cb6Feaa78dB6388506894F59621eB77";
        String referrerWallet = "0x00000000000000000000000000000000000000aa";
        when(depositWallets.depositWalletOf(WALLET)).thenReturn(depositWallet);
        when(depositWallets.depositWalletOf("0xReferrerEoa")).thenReturn(referrerWallet);
        when(fees.quote(any())).thenReturn(new com.polymarket.tma.fees.FeeService.FeeQuote(
                java.math.BigInteger.valueOf(100_000), 100, platform, null));
        var split = new com.polymarket.tma.referral.ReferralService.Split(
                9L, "0xReferrerEoa", java.math.BigInteger.valueOf(25_000));
        when(referrals.split(1L, java.math.BigInteger.valueOf(100_000))).thenReturn(Optional.of(split));
        var prepared = new com.polymarket.tma.trading.relayer.DepositWalletDtos.PreparedBatch("0xbatch", Map.of(), 2);
        when(depositWallets.prepareCollateralTransfers(eq(1L), eq("TRADING_FEE"), any())).thenReturn(prepared);

        service.prepare(1L, req(OrderDtos.SignatureType.POLY_1271, null));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, java.math.BigInteger>> transfers = ArgumentCaptor.forClass(Map.class);
        verify(depositWallets).prepareCollateralTransfers(eq(1L), eq("TRADING_FEE"), transfers.capture());
        assertThat(transfers.getValue()).containsExactly(
                Map.entry(platform, java.math.BigInteger.valueOf(75_000)),
                Map.entry(referrerWallet, java.math.BigInteger.valueOf(25_000)));
        verify(feeLedger).rememberPending("0xbatch", 1L, java.math.BigInteger.valueOf(75_000), split);
    }

    @Test
    void poly1271RejectsMakerOtherThanDepositWallet() {
        when(depositWallets.depositWalletOf(WALLET)).thenReturn("0x0000000000000000000000000000000000000dec");
        OrderDtos.PrepareOrderRequest req = req(OrderDtos.SignatureType.POLY_1271, PROXY);

        assertThatThrownBy(() -> service.prepare(1L, req))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("deposit wallet");

        verify(builder, never()).build(any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void poly1271ChargesPlatformFeeFromDepositWalletBatch() {
        when(depositWallets.depositWalletOf(WALLET)).thenReturn("0x0000000000000000000000000000000000000dec");
        var tx = new com.polymarket.tma.wallet.ApprovalDtos.UnsignedTx("TRADING_FEE_TRANSFER", "0xusdc", "0x", "0x0", 137);
        when(fees.quote(any())).thenReturn(new FeeService.FeeQuote(java.math.BigInteger.valueOf(25_000), 50, "0xfee", tx));
        var batch = new com.polymarket.tma.trading.relayer.DepositWalletDtos.PreparedBatch("0xhash", Map.of(), 1);
        when(depositWallets.prepareCollateralTransfers(1L, "TRADING_FEE",
                Map.of("0xfee", java.math.BigInteger.valueOf(25_000))))
                .thenReturn(batch);

        var resp = service.prepare(1L, req(OrderDtos.SignatureType.POLY_1271, null));

        assertThat(resp.feeBatch()).isSameAs(batch);
        assertThat(resp.feeTx()).as("no EOA transfer for deposit-wallet orders").isNull();
        assertThat(resp.feeAmountUsdc()).isEqualTo("0.025");
    }

    @Test
    void negRiskMarketPropagatesFlagToOrderBuilder() {
        OrderDtos.PrepareOrderRequest req = req(OrderDtos.SignatureType.EOA, null);
        when(marketService.get(eq("0xcid"))).thenReturn(Mono.just(stubMarket(true)));

        service.prepare(1L, req);

        verify(builder).build(eq(WALLET), eq(WALLET), sameOrder(req), eq(true));
    }

    @Test
    void marketLookupFailureDefaultsToRegularExchange() {
        OrderDtos.PrepareOrderRequest req = req(OrderDtos.SignatureType.EOA, null);
        when(marketService.get(eq("0xcid")))
                .thenReturn(Mono.error(new RuntimeException("gamma down")));

        service.prepare(1L, req);

        // Default is `false` so the user gets a clear CLOB error if the market really was negRisk
        // rather than the prepare endpoint crashing.
        verify(builder).build(eq(WALLET), eq(WALLET), sameOrder(req), eq(false));
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

    @Test
    void submitAuditsMarketConditionIdAndTokenIdSeparately() {
        Map<String, Object> wire = new LinkedHashMap<>();
        wire.put("tokenId", "110824900285475772037944791913429866572959836430651732935809182506257481598733");
        wire.put("side", 0);
        BuiltOrder built = new BuiltOrder("0xhash", java.math.BigInteger.valueOf(2_700_000),
                java.math.BigInteger.valueOf(5_000_000), stubTypedData(), wire, "0xcid");
        when(pending.get(1L, "0xhash")).thenReturn(built);
        when(builder.finalizeSignature(built, "0xsig")).thenReturn("0xfinal");
        when(clob.submit(eq(built), eq("0xfinal"), eq(WALLET), any()))
                .thenReturn(Mono.just(new OrderDtos.SubmittedOrderResponse("0xhash", "matched", null)));

        service.submit(1L, new OrderDtos.SubmitOrderRequest("0xhash", "0xsig", null)).block();

        var audit = ArgumentCaptor.forClass(com.polymarket.tma.trading.entity.OrderAudit.class);
        org.mockito.Mockito.verify(auditRepo, org.mockito.Mockito.times(2)).save(audit.capture());
        var saved = audit.getValue();
        assertThat(saved.getConditionId()).isEqualTo("0xcid");
        assertThat(saved.getTokenId()).isEqualTo(wire.get("tokenId"));
        assertThat(saved.getSide()).isEqualTo("BUY");
        assertThat(saved.getStatus()).isEqualTo("matched");
    }

    /** The service normalises size to 2 decimals, so match on content rather than identity. */
    private static OrderDtos.PrepareOrderRequest sameOrder(OrderDtos.PrepareOrderRequest req) {
        return org.mockito.ArgumentMatchers.argThat(r -> r != null
                && r.tokenId().equals(req.tokenId())
                && r.price().compareTo(req.price()) == 0
                && r.size().compareTo(req.size()) == 0
                && r.signatureType() == req.signatureType());
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
