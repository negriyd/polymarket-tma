package com.polymarket.tma.bets.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "platform_market")
@Getter
@Setter
@NoArgsConstructor
public class PlatformMarket {

    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_LOCKED = "LOCKED";
    public static final String STATUS_RESOLVED = "RESOLVED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    @Id
    @Column(name = "condition_id", length = 128)
    private String conditionId;

    @Column(name = "question", columnDefinition = "TEXT")
    private String question;

    @Column(name = "end_date")
    private Instant endDate;

    @Column(name = "status", nullable = false, length = 32)
    private String status = STATUS_OPEN;

    @Column(name = "winning_outcome_index")
    private Short winningOutcomeIndex;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolution_source", length = 16)
    private String resolutionSource;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
