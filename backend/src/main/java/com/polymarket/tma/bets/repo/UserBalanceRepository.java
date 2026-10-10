package com.polymarket.tma.bets.repo;

import com.polymarket.tma.bets.entity.UserBalance;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserBalanceRepository extends JpaRepository<UserBalance, Long> {}
