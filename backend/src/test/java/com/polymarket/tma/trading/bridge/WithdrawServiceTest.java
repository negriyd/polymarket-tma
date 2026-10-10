package com.polymarket.tma.trading.bridge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.polymarket.tma.auth.entity.AppUser;
import com.polymarket.tma.auth.repo.AppUserRepository;
import com.polymarket.tma.common.ApiException;
import com.polymarket.tma.trading.relayer.DepositWalletDtos;
import com.polymarket.tma.trading.relayer.DepositWalletService;
import com.polymarket.tma.trading.relayer.RelayerProperties;
import com.polymarket.tma.wallet.ApprovalStatusReader;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class WithdrawServiceTest {

    private static final String EOA = "0x415aE735eB964da56C11D0a53F0f082b85FcbEA5";
    private static final String WALLET = "0xa1D17A5546aD9166Cb14071c36a5741d812CbE57";
    private static final String PUSD = "0xC011a7E12a19f7B1f670d46F03B03f3342E82DFB";
    private static final String TRON_USDT = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t";
    private static final String TRON_RECIPIENT = "TRhETctHBXPxnMeGTsH1mGWnocPoMMYCvi";
    private static final String BRIDGE_EVM = "0x3876b3836e29D395b1e6cC360aBF7dA9501dFd35";
    private static final DepositWalletDtos.PreparedBatch BATCH = new DepositWalletDtos.PreparedBatch("0xhash", Map.of(), 1);

    @Mock private AppUserRepository userRepo;
    @Mock private DepositWalletService depositWallets;
    @Mock private FundingService funding;
    @Mock private BridgeClient bridge;
    @Mock private ApprovalStatusReader reader;

    private WithdrawService service;

    @BeforeEach
    void setUp() {
        AppUser user = new AppUser();
        user.setWalletAddress(EOA);
        lenient().when(userRepo.findById(1L)).thenReturn(Optional.of(user));
        lenient().when(depositWallets.depositWalletOf(EOA)).thenReturn(WALLET);
        lenient().when(funding.stablecoins()).thenReturn(List.of(new BridgeClient.SupportedAsset("728126428", "Tron",
                new BridgeClient.Token("Tether USD", "USDT", TRON_USDT, 6), 2.0)));
        lenient().when(reader.erc20Balance(PUSD, WALLET)).thenReturn(BigInteger.valueOf(4_593_915L));
        lenient().when(depositWallets.prepareCollateralTransfer(anyLong(), anyString(), anyString(), any())).thenReturn(BATCH);
        RelayerProperties relayer = new RelayerProperties(null, null, null, null, null, null, null,
                PUSD, null, null, null, null, null);
        service = new WithdrawService(userRepo, depositWallets, relayer, funding, bridge, reader);
    }

    @Test
    void optionsListDirectPusdFirstThenBridgedStablecoins() {
        var options = service.options(1L);

        assertThat(options.collateralBalance()).isEqualTo("4593915");
        assertThat(options.destinations()).extracting(WithdrawDtos.Destination::route, WithdrawDtos.Destination::symbol)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(WithdrawDtos.Route.DIRECT, "pUSD"),
                        org.assertj.core.groups.Tuple.tuple(WithdrawDtos.Route.BRIDGE, "USDT"));
    }

    @Test
    void directWithdrawalTransfersPusdToRecipient() {
        var prepared = service.prepare(1L, new WithdrawDtos.PrepareRequest("137", PUSD, EOA, "1000000"));

        assertThat(prepared.route()).isEqualTo(WithdrawDtos.Route.DIRECT);
        assertThat(prepared.sendTo()).isEqualTo(EOA);
        verify(depositWallets).prepareCollateralTransfer(1L, "WITHDRAW", EOA, BigInteger.valueOf(1_000_000));
        verify(bridge, never()).withdrawalAddresses(any(), any(), any(), any());
    }

    @Test
    void bridgedWithdrawalSendsPusdToBridgePolygonAddress() {
        when(bridge.withdrawalAddresses(WALLET, "728126428", TRON_USDT, TRON_RECIPIENT))
                .thenReturn(Mono.just(new BridgeClient.DepositAddresses(BRIDGE_EVM, "sol", "tron", "btc")));
        when(bridge.quote("137", PUSD, "3000000", "728126428", TRON_USDT, TRON_RECIPIENT))
                .thenReturn(Mono.just(new BridgeClient.Quote("2990000", 2.99, 25_000L, "q1")));

        var prepared = service.prepare(1L, new WithdrawDtos.PrepareRequest("728126428", TRON_USDT, TRON_RECIPIENT, "3000000"));

        assertThat(prepared.route()).isEqualTo(WithdrawDtos.Route.BRIDGE);
        assertThat(prepared.sendTo()).isEqualTo(BRIDGE_EVM);
        assertThat(prepared.estReceivedBaseUnit()).isEqualTo("2990000");
        assertThat(prepared.batch()).isSameAs(BATCH);
        verify(depositWallets).prepareCollateralTransfer(1L, "WITHDRAW", BRIDGE_EVM, BigInteger.valueOf(3_000_000));
    }

    @Test
    void quoteFailureDoesNotBlockWithdrawal() {
        when(bridge.withdrawalAddresses(any(), any(), any(), any()))
                .thenReturn(Mono.just(new BridgeClient.DepositAddresses(BRIDGE_EVM, null, null, null)));
        when(bridge.quote(any(), any(), any(), any(), any(), any())).thenReturn(Mono.error(new RuntimeException("no liquidity")));

        var prepared = service.prepare(1L, new WithdrawDtos.PrepareRequest("728126428", TRON_USDT, TRON_RECIPIENT, "3000000"));

        assertThat(prepared.estReceivedBaseUnit()).isNull();
        assertThat(prepared.sendTo()).isEqualTo(BRIDGE_EVM);
    }

    @Test
    void rejectsUnsupportedDestination() {
        assertThatThrownBy(() -> service.prepare(1L, new WithdrawDtos.PrepareRequest("1", "0xdead", EOA, "3000000")))
                .isInstanceOf(ApiException.class).hasMessageContaining("Unsupported destination");
    }

    @Test
    void rejectsRecipientOfWrongNetwork() {
        assertThatThrownBy(() -> service.prepare(1L, new WithdrawDtos.PrepareRequest("728126428", TRON_USDT, EOA, "3000000")))
                .isInstanceOf(ApiException.class).hasMessageContaining("valid Tron address");
    }

    @Test
    void rejectsAmountBelowBridgeMinimum() {
        assertThatThrownBy(() -> service.prepare(1L, new WithdrawDtos.PrepareRequest("728126428", TRON_USDT, TRON_RECIPIENT, "1500000")))
                .isInstanceOf(ApiException.class).hasMessageContaining("Minimum");
    }

    @Test
    void rejectsAmountAboveBalance() {
        assertThatThrownBy(() -> service.prepare(1L, new WithdrawDtos.PrepareRequest("137", PUSD, EOA, "5000000")))
                .isInstanceOf(ApiException.class).hasMessageContaining("exceeds");
        verify(depositWallets, never()).prepareCollateralTransfer(anyLong(), eq("WITHDRAW"), any(), any());
    }
}
