package com.polymarket.tma.bets.repo;

import com.polymarket.tma.bets.entity.Payout;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayoutRepository extends JpaRepository<Payout, Long> {

    Optional<Payout> findByBetId(Long betId);

    boolean existsByBetId(Long betId);
}
