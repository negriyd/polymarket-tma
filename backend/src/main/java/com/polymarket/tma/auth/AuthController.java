package com.polymarket.tma.auth;

import com.polymarket.tma.auth.dto.AuthDtos;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/telegram")
    public AuthDtos.TokenPair telegram(@Valid @RequestBody AuthDtos.TelegramLoginRequest req) {
        return auth.loginWithTelegram(req.initData());
    }

    @PostMapping("/refresh")
    public AuthDtos.TokenPair refresh(@Valid @RequestBody AuthDtos.RefreshRequest req) {
        return auth.refresh(req.refreshToken());
    }
}
