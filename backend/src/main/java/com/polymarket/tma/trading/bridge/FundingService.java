package com.polymarket.tma.trading.bridge;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polymarket.tma.auth.entity.AppUser;
import com.polymarket.tma.auth.repo.AppUserRepository;
import com.polymarket.tma.common.ApiException;
import com.polymarket.tma.trading.relayer.RelayerClient;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Funding a user's deposit wallet through the Polymarket Bridge: any supported stablecoin sent to
 * the per-wallet bridge address arrives as pUSD on Polygon — no gas, no manual wrapping.
 * Verified end-to-end on 2026-10-11 (USDT on Solana → 4.59 pUSD).
 */
@Service
public class FundingService {

    private static final Logger log = LoggerFactory.getLogger(FundingService.class);

    private static final Duration BRIDGE_TIMEOUT = Duration.ofSeconds(10);
    /** Bridge addresses are stable per wallet; cache to avoid a POST on every Wallet tab open. */
    private static final Duration ADDRESS_TTL = Duration.ofDays(7);
    private static final Duration ASSETS_TTL = Duration.ofHours(1);
    private static final String ADDRESS_PREFIX = "pm:bridge-addresses:";

    /** Stablecoins worth offering; the bridge also accepts volatile tokens we deliberately hide. */
    private static final Set<String> STABLECOINS = Set.of("USDC", "USDC.E", "USDT", "USDT0");
    /**
     * Networks we offer, cheap / common first. Others the bridge supports (Hypercore, Monad, Ink…)
     * are hidden: fewer ways to pick the wrong network, and no guessing their address family.
     */
    private static final List<String> CHAIN_ORDER =
            List.of("Polygon", "Tron", "Solana", "Base", "Arbitrum", "BNB Smart Chain", "Ethereum");

    private final AppUserRepository userRepo;
    private final RelayerClient relayer;
    private final BridgeClient bridge;
    private final RedisTemplate<String, Object> redis;
    private final ObjectMapper objectMapper;

    private volatile List<BridgeClient.SupportedAsset> assets = List.of();
    private volatile Instant assetsFetchedAt = Instant.EPOCH;

    public FundingService(AppUserRepository userRepo,
                          RelayerClient relayer,
                          BridgeClient bridge,
                          RedisTemplate<String, Object> redis,
                          ObjectMapper objectMapper) {
        this.userRepo = userRepo;
        this.relayer = relayer;
        this.bridge = bridge;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public FundingDtos.Funding funding(long userId) {
        String wallet = depositWallet(userId);
        BridgeClient.DepositAddresses addresses = bridgeAddresses(wallet);
        List<FundingDtos.Option> options = stablecoins().stream()
                .map(a -> new FundingDtos.Option(a.chainId(), a.chainName(), a.token().symbol(), a.token().address(),
                        a.token().decimals(), a.minCheckoutUsd(), addressFor(a.chainName(), addresses)))
                .filter(o -> o.depositAddress() != null)
                .toList();
        return new FundingDtos.Funding(wallet, addresses, options);
    }

    /** Recent bridge transfers into this user's deposit wallet, newest first. */
    public List<FundingDtos.Deposit> deposits(long userId) {
        BridgeClient.DepositAddresses a = bridgeAddresses(depositWallet(userId));
        List<FundingDtos.Deposit> out = Flux.fromStream(Stream.of(a.evm(), a.svm(), a.tron(), a.btc()))
                .filter(addr -> addr != null && !addr.isBlank())
                .flatMap(addr -> bridge.recentTransactions(addr)
                        .onErrorResume(e -> {
                            log.warn("Bridge status for {} failed: {}", addr, e.toString());
                            return Mono.just(List.of());
                        }))
                .flatMapIterable(list -> list)
                .map(t -> new FundingDtos.Deposit(t.fromChainId(), t.fromTokenAddress(), t.fromAmountBaseUnit(),
                        t.status(), t.txHash(), t.createdTimeMs()))
                .collectList()
                .block(BRIDGE_TIMEOUT);
        List<FundingDtos.Deposit> sorted = new ArrayList<>(out == null ? List.of() : out);
        // Freshly detected deposits carry no timestamp yet — they are the newest, so null sorts first.
        sorted.sort(Comparator.comparing(FundingDtos.Deposit::createdTimeMs,
                Comparator.nullsLast(Comparator.<Long>naturalOrder())).reversed());
        return sorted.size() > 20 ? sorted.subList(0, 20) : sorted;
    }

    private BridgeClient.DepositAddresses bridgeAddresses(String wallet) {
        String key = ADDRESS_PREFIX + wallet.toLowerCase();
        Object cached = redis.opsForValue().get(key);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached instanceof String s ? s : cached.toString(),
                        BridgeClient.DepositAddresses.class);
            } catch (JsonProcessingException e) {
                log.warn("Dropping unreadable cached bridge addresses for {}: {}", wallet, e.toString());
            }
        }
        BridgeClient.DepositAddresses fresh = bridge.depositAddresses(wallet).block(BRIDGE_TIMEOUT);
        if (fresh == null) {
            throw ApiException.upstream("BRIDGE_EMPTY_RESPONSE", "Polymarket bridge returned no deposit addresses");
        }
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(fresh), ADDRESS_TTL);
        } catch (JsonProcessingException e) {
            log.warn("Could not cache bridge addresses for {}: {}", wallet, e.toString());
        }
        return fresh;
    }

    /** Stablecoins on the networks we offer, cached for an hour; shared by deposits and withdrawals. */
    List<BridgeClient.SupportedAsset> stablecoins() {
        if (Instant.now().isAfter(assetsFetchedAt.plus(ASSETS_TTL))) {
            try {
                List<BridgeClient.SupportedAsset> all = bridge.supportedAssets().block(BRIDGE_TIMEOUT);
                if (all != null) {
                    assets = all.stream()
                            .filter(a -> a.token() != null && a.token().symbol() != null
                                    && STABLECOINS.contains(a.token().symbol().toUpperCase())
                                    && CHAIN_ORDER.contains(a.chainName()))
                            .sorted(Comparator.comparingInt(FundingService::chainRank)
                                    .thenComparing(a -> a.token().symbol()))
                            .toList();
                    assetsFetchedAt = Instant.now();
                }
            } catch (RuntimeException e) {
                // Keep serving the last known list; addresses alone are still useful.
                log.warn("Bridge supported-assets failed: {}", e.toString());
            }
        }
        return assets;
    }

    private static int chainRank(BridgeClient.SupportedAsset a) {
        return CHAIN_ORDER.indexOf(a.chainName());
    }

    /** Which bridge address family receives deposits from {@code chainName}. */
    static String addressFor(String chainName, BridgeClient.DepositAddresses a) {
        if (chainName == null) {
            return null;
        }
        return switch (chainName) {
            case "Solana" -> a.svm();
            case "Tron" -> a.tron();
            case "Bitcoin" -> a.btc();
            default -> a.evm();
        };
    }

    private String depositWallet(long userId) {
        AppUser user = userRepo.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("USER_GONE", "User not found"));
        if (user.getWalletAddress() == null || user.getWalletAddress().isBlank()) {
            throw ApiException.badRequest("WALLET_REQUIRED", "Wallet address must be set first");
        }
        return relayer.depositWalletAddress(user.getWalletAddress());
    }
}
