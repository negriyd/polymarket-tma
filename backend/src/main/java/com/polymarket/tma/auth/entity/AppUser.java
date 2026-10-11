package com.polymarket.tma.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "telegram_id", nullable = false, unique = true)
    private Long telegramId;

    @Column(name = "username", length = 64)
    private String username;

    @Column(name = "first_name", length = 128)
    private String firstName;

    @Column(name = "last_name", length = 128)
    private String lastName;

    @Column(name = "language_code", length = 8)
    private String languageCode;

    @Column(name = "is_premium", nullable = false)
    private boolean premium;

    @Column(name = "photo_url", length = 512)
    private String photoUrl;

    @Column(name = "wallet_address", length = 64)
    private String walletAddress;

    /** Public code in this user's invite links ({@code startapp=r<code>}); created on first use. */
    @Column(name = "ref_code", length = 16, unique = true)
    private String refCode;

    /** Who invited this user; set once, at sign-up. */
    @Column(name = "referred_by")
    private Long referredBy;

    @Column(name = "referred_at")
    private Instant referredAt;

    /** Channel tag of the link the user signed up through ({@code startapp=s<tag>}). */
    @Column(name = "signup_source", length = 32)
    private String signupSource;

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
