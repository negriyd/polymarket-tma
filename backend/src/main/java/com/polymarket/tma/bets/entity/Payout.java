package com.polymarket.tma.bets.entity;

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
@Table(name = "payout")
@Getter
@Setter
@NoArgsConstructor
public class Payout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bet_id", nullable = false, unique = true)
    private Long betId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "gross_micro", nullable = false)
    private BigInteger grossMicro;

    @Column(name = "fee_micro", nullable = false)
    private BigInteger feeMicro;

    @Column(name = "net_micro", nullable = false)
    private BigInteger netMicro;

    @Column(name = "credited_at", nullable = false, updatable = false)
    private Instant creditedAt;

    @PrePersist
    void onCreate() {
        this.creditedAt = Instant.now();
    }
}
