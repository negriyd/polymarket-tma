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
@Table(name = "bet")
@Getter
@Setter
@NoArgsConstructor
public class Bet {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_WON = "WON";
    public static final String STATUS_LOST = "LOST";
    public static final String STATUS_REFUNDED = "REFUNDED";
    public static final String STATUS_CLAIMED = "CLAIMED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "condition_id", nullable = false, length = 128)
    private String conditionId;

    /** 0 = first outcome (typically YES), 1 = second (typically NO). */
    @Column(name = "outcome_index", nullable = false)
    private short outcomeIndex;

    @Column(name = "amount_micro_usdc", nullable = false)
    private BigInteger amountMicroUsdc;

    @Column(name = "payment_tx_hash", unique = true, length = 128)
    private String paymentTxHash;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "idempotency_key", unique = true, length = 128)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
