package com.polymarket.tma.wallet;

import com.polymarket.tma.auth.entity.AppUser;
import com.polymarket.tma.auth.jwt.AuthPrincipal;
import com.polymarket.tma.auth.repo.AppUserRepository;
import com.polymarket.tma.common.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    private final AppUserRepository userRepo;

    public WalletController(AppUserRepository userRepo) {
        this.userRepo = userRepo;
    }

    public record SetAddressRequest(
            @Pattern(regexp = "^0x[a-fA-F0-9]{40}$", message = "must be a 0x EVM address")
            String address) {}

    public record WalletInfo(String address) {}

    @GetMapping
    public WalletInfo get(@AuthenticationPrincipal AuthPrincipal principal) {
        AppUser u = requireUser(principal);
        return new WalletInfo(u.getWalletAddress());
    }

    @PostMapping("/address")
    @Transactional
    public WalletInfo setAddress(@AuthenticationPrincipal AuthPrincipal principal,
                                  @Valid @RequestBody SetAddressRequest req) {
        AppUser u = requireUser(principal);
        u.setWalletAddress(req.address());
        userRepo.save(u);
        return new WalletInfo(u.getWalletAddress());
    }

    private AppUser requireUser(AuthPrincipal principal) {
        if (principal == null) {
            throw ApiException.unauthorized("UNAUTHENTICATED", "Authentication required");
        }
        return userRepo.findById(principal.userId())
                .orElseThrow(() -> ApiException.unauthorized("USER_GONE", "User not found"));
    }
}
