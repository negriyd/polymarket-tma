package com.polymarket.tma.auth.repo;

import com.polymarket.tma.auth.entity.AppUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByTelegramId(Long telegramId);
}
