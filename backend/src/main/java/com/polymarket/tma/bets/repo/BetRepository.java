package com.polymarket.tma.bets.repo;

import com.polymarket.tma.bets.entity.Bet;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BetRepository extends JpaRepository<Bet, Long> {

    List<Bet> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Bet> findByIdAndUserId(Long id, Long userId);

    Optional<Bet> findByIdempotencyKey(String idempotencyKey);

    Optional<Bet> findByPaymentTxHash(String paymentTxHash);

    List<Bet> findByConditionIdAndStatus(String conditionId, String status);

    @Query("""
            SELECT COALESCE(SUM(b.amountMicroUsdc), 0)
            FROM Bet b
            WHERE b.conditionId = :conditionId
              AND b.outcomeIndex = :outcomeIndex
              AND b.status = :status
            """)
    BigInteger sumAmountMicroByConditionAndOutcomeAndStatus(
            @Param("conditionId") String conditionId,
            @Param("outcomeIndex") short outcomeIndex,
            @Param("status") String status);

    @Query("""
            SELECT COALESCE(SUM(b.amountMicroUsdc), 0)
            FROM Bet b
            WHERE b.conditionId = :conditionId
              AND b.status = :status
            """)
    BigInteger sumAmountMicroByConditionAndStatus(
            @Param("conditionId") String conditionId,
            @Param("status") String status);
}
