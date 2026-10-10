package com.polymarket.tma.trading.bridge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polymarket.tma.auth.entity.AppUser;
import com.polymarket.tma.auth.repo.AppUserRepository;
import com.polymarket.tma.trading.bridge.BridgeClient.DepositAddresses;
import com.polymarket.tma.trading.bridge.BridgeClient.SupportedAsset;
import com.polymarket.tma.trading.bridge.BridgeClient.Token;
import com.polymarket.tma.trading.bridge.BridgeClient.Transaction;
import com.polymarket.tma.trading.relayer.RelayerClient;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class FundingServiceTest {

    private static final String EOA = "0x415aE735eB964da56C11D0a53F0f082b85FcbEA5";
    private static final String WALLET = "0xa1D17A5546aD9166Cb14071c36a5741d812CbE57";
    private static final DepositAddresses ADDRESSES = new DepositAddresses("0xevm", "solAddr", "tronAddr", "btcAddr");

    @Mock private AppUserRepository userRepo;
    @Mock private RelayerClient relayer;
    @Mock private BridgeClient bridge;
    @Mock private RedisTemplate<String, Object> redis;
    @Mock private ValueOperations<String, Object> values;

    private final ObjectMapper mapper = new ObjectMapper();
    private FundingService service;

    @BeforeEach
    void setUp() {
        AppUser user = new AppUser();
        user.setWalletAddress(EOA);
        lenient().when(userRepo.findById(1L)).thenReturn(Optional.of(user));
        lenient().when(relayer.depositWalletAddress(EOA)).thenReturn(WALLET);
        lenient().when(redis.opsForValue()).thenReturn(values);
        service = new FundingService(userRepo, relayer, bridge, redis, mapper);
    }

    private static SupportedAsset asset(String chain, String symbol) {
        return new SupportedAsset("1", chain, new Token(symbol, symbol, "0xtoken", 6), 2.0);
    }

    @Test
    void fundingMapsStablecoinsToMatchingBridgeAddressFamily() {
        when(bridge.depositAddresses(WALLET)).thenReturn(Mono.just(ADDRESSES));
        when(bridge.supportedAssets()).thenReturn(Mono.just(List.of(
                asset("Ethereum", "USDC"),
                asset("Solana", "USDT"),
                asset("Tron", "USDT"),
                asset("Polygon", "USDC.e"),
                asset("Polygon", "WETH"),
                asset("Monad", "USDC"),
                asset("Bitcoin", "BTC"))));

        var funding = service.funding(1L);

        assertThat(funding.depositWallet()).isEqualTo(WALLET);
        assertThat(funding.options())
                .extracting(FundingDtos.Option::chainName, FundingDtos.Option::symbol, FundingDtos.Option::depositAddress)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Polygon", "USDC.e", "0xevm"),
                        org.assertj.core.groups.Tuple.tuple("Tron", "USDT", "tronAddr"),
                        org.assertj.core.groups.Tuple.tuple("Solana", "USDT", "solAddr"),
                        org.assertj.core.groups.Tuple.tuple("Ethereum", "USDC", "0xevm"));
        verify(values).set(eq("pm:bridge-addresses:" + WALLET.toLowerCase()), anyString(), eq(Duration.ofDays(7)));
    }

    @Test
    void cachedBridgeAddressesSkipThePost() throws Exception {
        when(values.get("pm:bridge-addresses:" + WALLET.toLowerCase())).thenReturn(mapper.writeValueAsString(ADDRESSES));
        when(bridge.supportedAssets()).thenReturn(Mono.just(List.of()));

        assertThat(service.funding(1L).bridgeAddresses()).isEqualTo(ADDRESSES);
        verify(bridge, never()).depositAddresses(any());
    }

    @Test
    void depositsMergeAllFamiliesNewestFirstAndTolerateFailures() throws Exception {
        when(values.get(anyString())).thenReturn(mapper.writeValueAsString(ADDRESSES));
        when(bridge.recentTransactions("0xevm")).thenReturn(Mono.just(List.of(tx("COMPLETED", 100L))));
        when(bridge.recentTransactions("solAddr")).thenReturn(Mono.just(List.of(tx("COMPLETED", 300L), tx("DEPOSIT_DETECTED", null))));
        when(bridge.recentTransactions("tronAddr")).thenReturn(Mono.error(new RuntimeException("bridge down")));
        lenient().when(bridge.recentTransactions("btcAddr")).thenReturn(Mono.just(List.of()));

        var deposits = service.deposits(1L);

        assertThat(deposits).extracting(FundingDtos.Deposit::status, FundingDtos.Deposit::createdTimeMs)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("DEPOSIT_DETECTED", null),
                        org.assertj.core.groups.Tuple.tuple("COMPLETED", 300L),
                        org.assertj.core.groups.Tuple.tuple("COMPLETED", 100L));
    }

    @Test
    void addressFamilyMapping() {
        assertThat(FundingService.addressFor("Solana", ADDRESSES)).isEqualTo("solAddr");
        assertThat(FundingService.addressFor("Tron", ADDRESSES)).isEqualTo("tronAddr");
        assertThat(FundingService.addressFor("Bitcoin", ADDRESSES)).isEqualTo("btcAddr");
        assertThat(FundingService.addressFor("Base", ADDRESSES)).isEqualTo("0xevm");
    }

    private static Transaction tx(String status, Long createdTimeMs) {
        return new Transaction("1", "0xtoken", "1000000", "137", "0xpusd", status, null, createdTimeMs);
    }
}
