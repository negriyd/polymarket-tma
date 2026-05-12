package com.polymarket.tma.auth;

import com.polymarket.tma.auth.dto.AuthDtos;
import com.polymarket.tma.auth.entity.AppUser;
import com.polymarket.tma.auth.jwt.AuthPrincipal;
import com.polymarket.tma.auth.repo.AppUserRepository;
import com.polymarket.tma.common.ApiException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class MeController {

    private final AppUserRepository userRepo;

    public MeController(AppUserRepository userRepo) {
        this.userRepo = userRepo;
    }

    @GetMapping
    public AuthDtos.UserInfo me(@AuthenticationPrincipal AuthPrincipal principal) {
        if (principal == null) {
            throw ApiException.unauthorized("UNAUTHENTICATED", "Authentication required");
        }
        AppUser u = userRepo.findById(principal.userId())
                .orElseThrow(() -> ApiException.unauthorized("USER_GONE", "User not found"));
        return new AuthDtos.UserInfo(
                u.getId(), u.getTelegramId(), u.getUsername(), u.getFirstName(), u.getLastName(),
                u.getPhotoUrl(), u.getLanguageCode(), u.isPremium(), u.getWalletAddress()
        );
    }
}
