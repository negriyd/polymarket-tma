package com.polymarket.tma.bets.repo;

import com.polymarket.tma.bets.entity.Withdrawal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WithdrawalRepository extends JpaRepository<Withdrawal, Long> {

    List<Withdrawal> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Withdrawal> findFirstByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status);
}
