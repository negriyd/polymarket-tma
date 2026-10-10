package com.polymarket.tma.bets.repo;

import com.polymarket.tma.bets.entity.PlatformMarket;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PlatformMarketRepository extends JpaRepository<PlatformMarket, String> {

    List<PlatformMarket> findByStatusIn(Collection<String> statuses);

    @Query("""
            SELECT DISTINCT pm FROM PlatformMarket pm
            WHERE pm.status IN ('OPEN', 'LOCKED')
              AND EXISTS (
                  SELECT 1 FROM Bet b
                  WHERE b.conditionId = pm.conditionId AND b.status = 'CONFIRMED'
              )
            """)
    List<PlatformMarket> findActiveMarketsWithConfirmedBets();
}
