package com.polymarket.tma.bets;

import com.polymarket.tma.auth.entity.AppUser;
import com.polymarket.tma.auth.repo.AppUserRepository;
import com.polymarket.tma.bets.dto.BetDtos;
import com.polymarket.tma.bets.entity.Withdrawal;
import com.polymarket.tma.bets.repo.WithdrawalRepository;
import com.polymarket.tma.common.ApiException;
import com.polymarket.tma.config.AppProperties;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WithdrawService {

    private static final Logger log = LoggerFactory.getLogger(WithdrawService.class);
    private static final BigInteger USDC_MICRO = BigInteger.valueOf(1_000_000L);

    private final AppProperties props;
    private final AppUserRepository userRepo;
    private final UserBalanceService balanceService;
    private final WithdrawalRepository withdrawalRepo;
    private final UsdcTreasurySender treasurySender;

    public WithdrawService(AppProperties props,
                           AppUserRepository userRepo,
                           UserBalanceService balanceService,
                           WithdrawalRepository withdrawalRepo,
                           UsdcTreasurySender treasurySender) {
        this.props = props;
        this.userRepo = userRepo;
        this.balanceService = balanceService;
        this.withdrawalRepo = withdrawalRepo;
        this.treasurySender = treasurySender;
    }

    @Transactional(readOnly = true)
    public BetDtos.BalanceResponse balance(long userId) {
        return new BetDtos.BalanceResponse(
                MarketPoolService.toUsdcString(balanceService.available(userId)),
                treasurySender.canSend());
    }

    @Transactional(readOnly = true)
    public List<BetDtos.WithdrawalResponse> history(long userId) {
        return withdrawalRepo.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    public BetDtos.WithdrawalResponse withdraw(long userId, BetDtos.WithdrawRequest req) {
        requireBetsEnabled();
        AppUser user = requireWallet(userId);
        BigInteger amountMicro = toMicroUsdc(req.amountUsdc());
        requireMinWithdraw(amountMicro);
        enforceRateLimit(userId);

        // Debit first so we never double-send on retry.
        balanceService.debit(userId, amountMicro);

        Withdrawal row = new Withdrawal();
        row.setUserId(userId);
        row.setAmountMicro(amountMicro);
        row.setStatus(Withdrawal.STATUS_PENDING);
        withdrawalRepo.save(row);

        try {
            String txHash = treasurySender.sendUsdc(user.getWalletAddress(), amountMicro);
            row.setTxHash(txHash);
            row.setStatus(Withdrawal.STATUS_SENT);
            row.setSentAt(Instant.now());
            withdrawalRepo.save(row);
            return toResponse(row);
        } catch (RuntimeException e) {
            balanceService.refundDebit(userId, amountMicro);
            row.setStatus(Withdrawal.STATUS_FAILED);
            withdrawalRepo.save(row);
            log.warn("Withdraw failed for user {} amountMicro={}: {}", userId, amountMicro, e.toString());
            throw e;
        }
    }

    private void enforceRateLimit(long userId) {
        int cooldownMin = props.bets().withdrawCooldownMinutesOrDefault();
        withdrawalRepo.findFirstByUserIdAndStatusOrderByCreatedAtDesc(userId, Withdrawal.STATUS_SENT)
                .ifPresent(last -> {
                    Instant allowedAfter = last.getSentAt() != null
                            ? last.getSentAt()
                            : last.getCreatedAt();
                    allowedAfter = allowedAfter.plus(Duration.ofMinutes(cooldownMin));
                    if (Instant.now().isBefore(allowedAfter)) {
                        throw ApiException.badRequest("WITHDRAW_RATE_LIMIT",
                                "Please wait %d minutes between withdrawals".formatted(cooldownMin));
                    }
                });
    }

    private void requireMinWithdraw(BigInteger amountMicro) {
        BigDecimal min = props.bets().minWithdrawUsdc() == null ? BigDecimal.ONE : props.bets().minWithdrawUsdc();
        if (amountMicro.compareTo(toMicroUsdc(min)) < 0) {
            throw ApiException.badRequest("WITHDRAW_TOO_SMALL",
                    "Minimum withdraw is %s USDC".formatted(min.toPlainString()));
        }
    }

    private void requireBetsEnabled() {
        if (props.bets() == null || !props.bets().enabled()) {
            throw ApiException.badRequest("BETS_DISABLED", "Internal betting is not enabled");
        }
    }

    private AppUser requireWallet(long userId) {
        AppUser user = userRepo.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("USER_GONE", "User not found"));
        if (user.getWalletAddress() == null || user.getWalletAddress().isBlank()) {
            throw ApiException.badRequest("WALLET_REQUIRED", "Wallet address required for withdraw");
        }
        return user;
    }

    private BetDtos.WithdrawalResponse toResponse(Withdrawal w) {
        return new BetDtos.WithdrawalResponse(
                w.getId(),
                MarketPoolService.toUsdcString(w.getAmountMicro()),
                w.getStatus(),
                w.getTxHash(),
                w.getCreatedAt(),
                w.getSentAt());
    }

    private static BigInteger toMicroUsdc(BigDecimal amount) {
        return amount.multiply(new BigDecimal(USDC_MICRO)).setScale(0, RoundingMode.HALF_UP).toBigInteger();
    }
}
