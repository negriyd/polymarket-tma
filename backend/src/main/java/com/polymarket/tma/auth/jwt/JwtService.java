package com.polymarket.tma.auth.jwt;

import com.polymarket.tma.common.ApiException;
import com.polymarket.tma.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AppProperties props;
    private final SecretKey signingKey;

    public JwtService(AppProperties props) {
        this.props = props;
        byte[] secretBytes = props.jwt().secret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("app.jwt.secret must be at least 32 bytes long");
        }
        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
    }

    public String issueAccessToken(long userId, String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(props.jwt().issuer())
                .subject(Long.toString(userId))
                .claim("username", username)
                .claim("typ", "access")
                .issuedAt(java.util.Date.from(now))
                .expiration(java.util.Date.from(now.plus(props.jwt().accessTtl())))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public String generateRefreshToken() {
        byte[] buf = new byte[48];
        RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    public Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(props.jwt().issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException e) {
            throw ApiException.unauthorized("JWT_INVALID", "JWT is invalid: " + e.getMessage());
        }
    }

    public long extractUserId(Claims claims) {
        return Long.parseLong(claims.getSubject());
    }
}
