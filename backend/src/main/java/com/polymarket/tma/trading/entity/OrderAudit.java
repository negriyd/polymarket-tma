package com.polymarket.tma.trading.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigInteger;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "order_audit")
@Getter
@Setter
@NoArgsConstructor
public class OrderAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "order_hash", length = 128)
    private String orderHash;

    @Column(name = "condition_id", nullable = false, length = 128)
    private String conditionId;

    @Column(name = "side", nullable = false, length = 8)
    private String side;

    @Column(name = "maker_amount", nullable = false)
    private BigInteger makerAmount;

    @Column(name = "taker_amount", nullable = false)
    private BigInteger takerAmount;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "idempotency_key", unique = true, length = 128)
    private String idempotencyKey;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
