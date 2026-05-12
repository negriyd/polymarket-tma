package com.polymarket.tma.auth;

import com.polymarket.tma.auth.dto.AuthDtos;
import com.polymarket.tma.auth.entity.AppUser;
import com.polymarket.tma.auth.entity.RefreshToken;
import com.polymarket.tma.auth.jwt.JwtService;
import com.polymarket.tma.auth.repo.AppUserRepository;
import com.polymarket.tma.auth.repo.RefreshTokenRepository;
import com.polymarket.tma.auth.telegram.TelegramInitData;
import com.polymarket.tma.auth.telegram.TelegramInitDataValidator;
import com.polymarket.tma.auth.telegram.TelegramUser;
import com.polymarket.tma.common.ApiException;
import com.polymarket.tma.config.AppProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AppUserRepository userRepo;
    private final RefreshTokenRepository refreshRepo;
    private final TelegramInitDataValidator validator;
    private final JwtService jwt;
    private final AppProperties props;

    public AuthService(AppUserRepository userRepo,
                       RefreshTokenRepository refreshRepo,
                       TelegramInitDataValidator validator,
                       JwtService jwt,
                       AppProperties props) {
        this.userRepo = userRepo;
        this.refreshRepo = refreshRepo;
        this.validator = validator;
        this.jwt = jwt;
        this.props = props;
    }

    @Transactional
    public AuthDtos.TokenPair loginWithTelegram(String initDataRaw) {
        TelegramInitData parsed = validator.validate(initDataRaw);
        AppUser user = upsertUser(parsed.user());
        return issueTokens(user);
    }

    @Transactional
    public AuthDtos.TokenPair refresh(String refreshTokenRaw) {
        String hash = sha256(refreshTokenRaw);
        RefreshToken token = refreshRepo.findByTokenHash(hash)
                .orElseThrow(() -> ApiException.unauthorized("REFRESH_NOT_FOUND", "Refresh token not found"));
        if (token.isRevoked() || token.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.unauthorized("REFRESH_EXPIRED", "Refresh token expired or revoked");
        }
        AppUser user = userRepo.findById(token.getUserId())
                .orElseThrow(() -> ApiException.unauthorized("USER_GONE", "User no longer exists"));
        // Rotate.
        token.setRevoked(true);
        refreshRepo.save(token);
        return issueTokens(user);
    }

    private AppUser upsertUser(TelegramUser tg) {
        AppUser user = userRepo.findByTelegramId(tg.id()).orElseGet(AppUser::new);
        user.setTelegramId(tg.id());
        user.setFirstName(tg.firstName());
        user.setLastName(tg.lastName());
        user.setUsername(tg.username());
        user.setLanguageCode(tg.languageCode());
        user.setPremium(Boolean.TRUE.equals(tg.premium()));
        user.setPhotoUrl(tg.photoUrl());
        return userRepo.save(user);
    }

    private AuthDtos.TokenPair issueTokens(AppUser user) {
        String access = jwt.issueAccessToken(user.getId(), user.getUsername());
        String refresh = jwt.generateRefreshToken();

        RefreshToken rt = new RefreshToken();
        rt.setUserId(user.getId());
        rt.setTokenHash(sha256(refresh));
        rt.setExpiresAt(Instant.now().plus(props.jwt().refreshTtl()));
        refreshRepo.save(rt);

        return new AuthDtos.TokenPair(
                access,
                refresh,
                props.jwt().accessTtl().toSeconds(),
                new AuthDtos.UserInfo(
                        user.getId(),
                        user.getTelegramId(),
                        user.getUsername(),
                        user.getFirstName(),
                        user.getLastName(),
                        user.getPhotoUrl(),
                        user.getLanguageCode(),
                        user.isPremium(),
                        user.getWalletAddress()
                )
        );
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
