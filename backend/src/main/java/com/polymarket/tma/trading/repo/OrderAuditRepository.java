package com.polymarket.tma.trading.repo;

import com.polymarket.tma.trading.entity.OrderAudit;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderAuditRepository extends JpaRepository<OrderAudit, Long> {
    Optional<OrderAudit> findByIdempotencyKey(String key);
}
