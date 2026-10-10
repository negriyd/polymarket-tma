package com.polymarket.tma.bets;

import com.polymarket.tma.auth.entity.AppUser;
import com.polymarket.tma.auth.repo.AppUserRepository;
import com.polymarket.tma.bets.dto.BetDtos;
import com.polymarket.tma.bets.entity.Bet;
import com.polymarket.tma.bets.entity.PlatformMarket;
import com.polymarket.tma.bets.repo.BetRepository;
import com.polymarket.tma.bets.repo.PlatformMarketRepository;
import com.polymarket.tma.common.ApiException;
import com.polymarket.tma.config.AppProperties;
import com.polymarket.tma.fees.FeeCalldataBuilder;
import com.polymarket.tma.market.MarketService;
import com.polymarket.tma.market.dto.MarketDto;
import com.polymarket.tma.wallet.ApprovalDtos;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BetService {

    private static final Logger log = LoggerFactory.getLogger(BetService.class);

    private static final BigInteger USDC_MICRO = BigInteger.valueOf(1_000_000L);
    private static final int POLYGON_CHAIN_ID = 137;

    private final AppProperties props;
    private final AppUserRepository userRepo;
    private final BetRepository betRepo;
    private final PlatformMarketRepository marketRepo;
    private final MarketService marketService;
    private final UsdcTransferVerifier transferVerifier;

    public BetService(AppProperties props,
                      AppUserRepository userRepo,
                      BetRepository betRepo,
                      PlatformMarketRepository marketRepo,
                      MarketService marketService,
                      UsdcTransferVerifier transferVerifier) {
        this.props = props;
        this.userRepo = userRepo;
        this.betRepo = betRepo;
        this.marketRepo = marketRepo;
        this.marketService = marketService;
        this.transferVerifier = transferVerifier;
    }

    public BetDtos.ConfigResponse config() {
        AppProperties.Bets cfg = props.bets();
        if (cfg == null) {
            return new BetDtos.ConfigResponse(false, 0, "1", "1", 15);
        }
        BigDecimal minBet = cfg.minBetUsdc() == null ? BigDecimal.ONE : cfg.minBetUsdc();
        BigDecimal minWithdraw = cfg.minWithdrawUsdc() == null ? BigDecimal.ONE : cfg.minWithdrawUsdc();
        return new BetDtos.ConfigResponse(
                cfg.enabled(),
                cfg.platformFeeBpsOrZero(),
                minBet.toPlainString(),
                minWithdraw.toPlainString(),
                cfg.lockBeforeEndMinutesOrDefault());
    }

    @Transactional
    public BetDtos.PrepareBetResponse prepare(long userId, BetDtos.PrepareBetRequest req) {
        requireBetsEnabled();
        AppUser user = requireWallet(userId);

        if (req.idempotencyKey() != null && !req.idempotencyKey().isBlank()) {
            Optional<Bet> existing = betRepo.findByIdempotencyKey(req.idempotencyKey());
            if (existing.isPresent()) {
                Bet bet = existing.get();
                if (!bet.getUserId().equals(userId)) {
                    throw ApiException.badRequest("IDEMPOTENCY_CONFLICT", "Idempotency key already used");
                }
                if (Bet.STATUS_PENDING.equals(bet.getStatus())) {
                    return toPrepareResponse(bet, outcomeLabel(bet.getConditionId(), bet.getOutcomeIndex()));
                }
                if (Bet.STATUS_CONFIRMED.equals(bet.getStatus())) {
                    throw ApiException.badRequest("BET_ALREADY_CONFIRMED", "Bet already confirmed");
                }
            }
        }

        MarketDto market = loadMarket(req.conditionId());
        validateMarketForBetting(market);
        PlatformMarket platformMarket = upsertPlatformMarket(market);
        if (!PlatformMarket.STATUS_OPEN.equals(platformMarket.getStatus())) {
            throw ApiException.badRequest("MARKET_NOT_OPEN", "Market is not accepting bets");
        }

        BigInteger amountMicro = toMicroUsdc(req.amountUsdc());
        requireMinBet(amountMicro);

        Bet bet = new Bet();
        bet.setUserId(userId);
        bet.setConditionId(platformMarket.getConditionId());
        bet.setOutcomeIndex((short) req.outcomeIndex());
        bet.setAmountMicroUsdc(amountMicro);
        bet.setStatus(Bet.STATUS_PENDING);
        bet.setIdempotencyKey(blankToNull(req.idempotencyKey()));
        betRepo.save(bet);

        String treasury = props.bets().treasuryAddress();
        String calldata = FeeCalldataBuilder.erc20Transfer(treasury, amountMicro);
        ApprovalDtos.UnsignedTx tx = new ApprovalDtos.UnsignedTx(
                "BET_USDC_TRANSFER",
                props.polygon().usdcAddress(),
                calldata,
                "0x0",
                POLYGON_CHAIN_ID);

        String label = outcomeLabel(market, req.outcomeIndex());
        log.info("Prepared bet id={} user={} conditionId={} outcome={} amountMicro={}",
                bet.getId(), userId, bet.getConditionId(), req.outcomeIndex(), amountMicro);

        return new BetDtos.PrepareBetResponse(
                bet.getId(),
                bet.getConditionId(),
                req.outcomeIndex(),
                label,
                req.amountUsdc().stripTrailingZeros().toPlainString(),
                props.bets().platformFeeBpsOrZero(),
                tx);
    }

    @Transactional
    public BetDtos.BetResponse confirm(long userId, BetDtos.ConfirmBetRequest req) {
        requireBetsEnabled();
        AppUser user = requireWallet(userId);

        Bet bet = betRepo.findByIdAndUserId(req.betId(), userId)
                .orElseThrow(() -> ApiException.notFound("BET_NOT_FOUND", "Bet not found"));

        if (Bet.STATUS_CONFIRMED.equals(bet.getStatus())) {
            if (req.paymentTxHash().equalsIgnoreCase(bet.getPaymentTxHash())) {
                return toBetResponse(bet, outcomeLabel(bet.getConditionId(), bet.getOutcomeIndex()));
            }
            throw ApiException.badRequest("BET_ALREADY_CONFIRMED", "Bet already confirmed with a different tx");
        }
        if (!Bet.STATUS_PENDING.equals(bet.getStatus())) {
            throw ApiException.badRequest("BET_NOT_PENDING", "Bet is not pending confirmation");
        }

        Optional<Bet> txUsed = betRepo.findByPaymentTxHash(normalizeTxHash(req.paymentTxHash()));
        if (txUsed.isPresent() && !txUsed.get().getId().equals(bet.getId())) {
            throw ApiException.badRequest("TX_ALREADY_USED", "Transaction hash already linked to another bet");
        }

        PlatformMarket platformMarket = marketRepo.findById(bet.getConditionId())
                .orElseThrow(() -> ApiException.notFound("MARKET_NOT_FOUND", "Platform market missing"));
        if (!PlatformMarket.STATUS_OPEN.equals(platformMarket.getStatus())) {
            throw ApiException.badRequest("MARKET_NOT_OPEN", "Market is not accepting bets");
        }

        var verified = transferVerifier.verify(
                req.paymentTxHash(),
                user.getWalletAddress(),
                props.bets().treasuryAddress(),
                bet.getAmountMicroUsdc());
        if (verified.isEmpty()) {
            throw ApiException.badRequest("PAYMENT_NOT_VERIFIED",
                    "Could not verify USDC transfer. Check tx hash, amount, and treasury address.");
        }

        bet.setPaymentTxHash(verified.get().txHash());
        bet.setStatus(Bet.STATUS_CONFIRMED);
        bet.setConfirmedAt(Instant.now());
        betRepo.save(bet);

        log.info("Confirmed bet id={} user={} tx={}", bet.getId(), userId, bet.getPaymentTxHash());
        return toBetResponse(bet, outcomeLabel(bet.getConditionId(), bet.getOutcomeIndex()));
    }

    @Transactional(readOnly = true)
    public List<BetDtos.BetResponse> mine(long userId) {
        return betRepo.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(b -> toBetResponse(b, outcomeLabel(b.getConditionId(), b.getOutcomeIndex())))
                .toList();
    }

    private BetDtos.PrepareBetResponse toPrepareResponse(Bet bet, String label) {
        BigDecimal amount = new BigDecimal(bet.getAmountMicroUsdc())
                .divide(new BigDecimal(USDC_MICRO), 6, RoundingMode.HALF_UP);
        String treasury = props.bets().treasuryAddress();
        String calldata = FeeCalldataBuilder.erc20Transfer(treasury, bet.getAmountMicroUsdc());
        ApprovalDtos.UnsignedTx tx = new ApprovalDtos.UnsignedTx(
                "BET_USDC_TRANSFER",
                props.polygon().usdcAddress(),
                calldata,
                "0x0",
                POLYGON_CHAIN_ID);
        return new BetDtos.PrepareBetResponse(
                bet.getId(),
                bet.getConditionId(),
                bet.getOutcomeIndex(),
                label,
                amount.stripTrailingZeros().toPlainString(),
                props.bets().platformFeeBpsOrZero(),
                tx);
    }

    private BetDtos.BetResponse toBetResponse(Bet bet, String label) {
        return new BetDtos.BetResponse(
                bet.getId(),
                bet.getConditionId(),
                bet.getOutcomeIndex(),
                label,
                MarketPoolService.toUsdcString(bet.getAmountMicroUsdc()),
                bet.getStatus(),
                bet.getPaymentTxHash(),
                bet.getCreatedAt(),
                bet.getConfirmedAt());
    }

    private void requireBetsEnabled() {
        AppProperties.Bets cfg = props.bets();
        if (cfg == null || !cfg.enabled()) {
            throw ApiException.badRequest("BETS_DISABLED",
                    "Internal betting is not enabled. Set BETS_ENABLED=true and BETS_TREASURY_ADDRESS.");
        }
    }

    private AppUser requireWallet(long userId) {
        AppUser user = userRepo.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("USER_GONE", "User not found"));
        if (user.getWalletAddress() == null || user.getWalletAddress().isBlank()) {
            throw ApiException.badRequest("WALLET_REQUIRED",
                    "Wallet address must be set before placing bets");
        }
        return user;
    }

    private MarketDto loadMarket(String conditionId) {
        try {
            return marketService.get(conditionId).block(Duration.ofSeconds(5));
        } catch (RuntimeException e) {
            throw ApiException.notFound("MARKET_NOT_FOUND", "Market not found: " + conditionId);
        }
    }

    private void validateMarketForBetting(MarketDto market) {
        if (Boolean.TRUE.equals(market.negRisk())) {
            throw ApiException.badRequest("BET_NEG_RISK_UNSUPPORTED",
                    "Multi-outcome (negRisk) markets are not supported in MVP");
        }
        List<String> outcomes = market.outcomes();
        if (outcomes == null || outcomes.size() != 2) {
            throw ApiException.badRequest("BET_NOT_BINARY",
                    "Only binary YES/NO markets are supported");
        }
        if (Boolean.FALSE.equals(market.active()) || Boolean.TRUE.equals(market.closed())) {
            throw ApiException.badRequest("MARKET_CLOSED", "Market is closed");
        }
        Instant endDate = market.endDate();
        if (endDate != null) {
            int lockMinutes = props.bets().lockBeforeEndMinutesOrDefault();
            Instant lockAt = endDate.minus(Duration.ofMinutes(lockMinutes));
            if (Instant.now().isAfter(lockAt)) {
                throw ApiException.badRequest("MARKET_LOCKED",
                        "Betting closed %d minutes before market end".formatted(lockMinutes));
            }
        }
    }

    private PlatformMarket upsertPlatformMarket(MarketDto dto) {
        PlatformMarket row = marketRepo.findById(dto.conditionId()).orElseGet(() -> {
            PlatformMarket m = new PlatformMarket();
            m.setConditionId(dto.conditionId());
            return m;
        });
        row.setQuestion(dto.question());
        row.setEndDate(dto.endDate());
        if (row.getStatus() == null) {
            row.setStatus(PlatformMarket.STATUS_OPEN);
        }
        row.setEnabled(true);
        return marketRepo.save(row);
    }

    private void requireMinBet(BigInteger amountMicro) {
        BigDecimal min = props.bets().minBetUsdc() == null ? BigDecimal.ONE : props.bets().minBetUsdc();
        BigInteger minMicro = toMicroUsdc(min);
        if (amountMicro.compareTo(minMicro) < 0) {
            throw ApiException.badRequest("BET_TOO_SMALL",
                    "Minimum bet is %s USDC".formatted(min.toPlainString()));
        }
    }

    private static BigInteger toMicroUsdc(BigDecimal amount) {
        return amount.multiply(new BigDecimal(USDC_MICRO)).setScale(0, RoundingMode.HALF_UP).toBigInteger();
    }

    private String outcomeLabel(String conditionId, int outcomeIndex) {
        try {
            MarketDto market = marketService.get(conditionId).block(Duration.ofSeconds(3));
            return outcomeLabel(market, outcomeIndex);
        } catch (RuntimeException e) {
            return outcomeIndex == 0 ? "YES" : "NO";
        }
    }

    private static String outcomeLabel(MarketDto market, int outcomeIndex) {
        List<String> outcomes = market.outcomes();
        if (outcomes != null && outcomeIndex >= 0 && outcomeIndex < outcomes.size()) {
            return outcomes.get(outcomeIndex);
        }
        return outcomeIndex == 0 ? "YES" : "NO";
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String normalizeTxHash(String hash) {
        String h = hash.trim();
        return h.startsWith("0x") ? h : "0x" + h;
    }
}
