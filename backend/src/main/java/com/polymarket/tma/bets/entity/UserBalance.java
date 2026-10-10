package com.polymarket.tma.bets.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigInteger;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_balance")
@Getter
@Setter
@NoArgsConstructor
public class UserBalance {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "available_micro_usdc", nullable = false)
    private BigInteger availableMicroUsdc = BigInteger.ZERO;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }
}
