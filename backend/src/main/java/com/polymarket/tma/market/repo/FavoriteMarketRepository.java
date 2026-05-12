package com.polymarket.tma.market.repo;

import com.polymarket.tma.market.entity.FavoriteMarket;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface FavoriteMarketRepository extends JpaRepository<FavoriteMarket, Long> {

    List<FavoriteMarket> findAllByUserId(Long userId);

    Optional<FavoriteMarket> findByUserIdAndConditionId(Long userId, String conditionId);

    @Transactional
    long deleteByUserIdAndConditionId(Long userId, String conditionId);
}
