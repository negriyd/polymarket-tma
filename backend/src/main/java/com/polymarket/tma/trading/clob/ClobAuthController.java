package com.polymarket.tma.trading.clob;

import com.polymarket.tma.auth.jwt.AuthPrincipal;
import com.polymarket.tma.common.ApiException;
import com.polymarket.tma.trading.dto.ClobAuthDtos;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Endpoints for the CLOB L1 key-derivation flow:
 * <pre>
 *   POST /api/clob/auth/prepare  -> typed data the wallet signs
 *   POST /api/clob/auth/submit   -> signature + echoed metadata, stores credentials
 *   GET  /api/clob/auth/status   -> whether the user has credentials cached
 *   DELETE /api/clob/auth        -> wipe credentials for the current user
 * </pre>
 */
@RestController
@RequestMapping("/api/clob/auth")
public class ClobAuthController {

    private final ClobAuthService service;

    public ClobAuthController(ClobAuthService service) {
        this.service = service;
    }

    @PostMapping("/prepare")
    public ClobAuthDtos.PrepareResponse prepare(@AuthenticationPrincipal AuthPrincipal principal) {
        requireAuth(principal);
        return service.prepare(principal.userId());
    }

    @PostMapping("/submit")
    public Mono<ClobAuthDtos.StatusResponse> submit(@AuthenticationPrincipal AuthPrincipal principal,
                                                    @Valid @RequestBody ClobAuthDtos.SubmitRequest req) {
        requireAuth(principal);
        return service.submit(principal.userId(), req);
    }

    @GetMapping("/status")
    public ClobAuthDtos.StatusResponse status(@AuthenticationPrincipal AuthPrincipal principal) {
        requireAuth(principal);
        return service.status(principal.userId());
    }

    @DeleteMapping
    public ResponseEntity<Void> revoke(@AuthenticationPrincipal AuthPrincipal principal) {
        requireAuth(principal);
        service.revoke(principal.userId());
        return ResponseEntity.noContent().build();
    }

    private static void requireAuth(AuthPrincipal principal) {
        if (principal == null) {
            throw ApiException.unauthorized("UNAUTHENTICATED", "Authentication required");
        }
    }
}
