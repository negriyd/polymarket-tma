package com.polymarket.tma.bets;

import com.polymarket.tma.bets.entity.UserBalance;
import com.polymarket.tma.bets.repo.UserBalanceRepository;
import com.polymarket.tma.common.ApiException;
import java.math.BigInteger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserBalanceService {

    private final UserBalanceRepository balanceRepo;

    public UserBalanceService(UserBalanceRepository balanceRepo) {
        this.balanceRepo = balanceRepo;
    }

    @Transactional(readOnly = true)
    public BigInteger available(long userId) {
        return balanceRepo.findById(userId)
                .map(UserBalance::getAvailableMicroUsdc)
                .orElse(BigInteger.ZERO);
    }

    @Transactional
    public void credit(long userId, BigInteger amountMicro) {
        if (amountMicro == null || amountMicro.signum() <= 0) {
            return;
        }
        UserBalance row = balanceRepo.findById(userId).orElseGet(() -> {
            UserBalance b = new UserBalance();
            b.setUserId(userId);
            b.setAvailableMicroUsdc(BigInteger.ZERO);
            return b;
        });
        row.setAvailableMicroUsdc(row.getAvailableMicroUsdc().add(amountMicro));
        balanceRepo.save(row);
    }

    @Transactional
    public void debit(long userId, BigInteger amountMicro) {
        if (amountMicro == null || amountMicro.signum() <= 0) {
            throw ApiException.badRequest("INVALID_AMOUNT", "Withdraw amount must be positive");
        }
        UserBalance row = balanceRepo.findById(userId).orElseGet(() -> {
            UserBalance b = new UserBalance();
            b.setUserId(userId);
            b.setAvailableMicroUsdc(BigInteger.ZERO);
            return b;
        });
        if (row.getAvailableMicroUsdc().compareTo(amountMicro) < 0) {
            throw ApiException.badRequest("INSUFFICIENT_BALANCE", "Not enough internal balance");
        }
        row.setAvailableMicroUsdc(row.getAvailableMicroUsdc().subtract(amountMicro));
        balanceRepo.save(row);
    }

    @Transactional
    public void refundDebit(long userId, BigInteger amountMicro) {
        credit(userId, amountMicro);
    }
}
