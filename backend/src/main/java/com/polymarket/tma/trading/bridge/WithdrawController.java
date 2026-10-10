package com.polymarket.tma.trading.bridge;

import com.polymarket.tma.auth.jwt.AuthPrincipal;
import com.polymarket.tma.common.ApiException;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <pre>
 *   GET  /api/wallet/deposit-wallet/withdraw/options  -> balance + destinations (pUSD direct, bridged stablecoins)
 *   POST /api/wallet/deposit-wallet/withdraw/prepare  -> batch to sign; submit via .../batches/submit
 * </pre>
 */
@RestController
@RequestMapping("/api/wallet/deposit-wallet/withdraw")
public class WithdrawController {

    private final BridgeWithdrawService service;

    public WithdrawController(BridgeWithdrawService service) {
        this.service = service;
    }

    @GetMapping("/options")
    public WithdrawDtos.Options options(@AuthenticationPrincipal AuthPrincipal principal) {
        return service.options(userId(principal));
    }

    @PostMapping("/prepare")
    public WithdrawDtos.Prepared prepare(@AuthenticationPrincipal AuthPrincipal principal,
                                         @Valid @RequestBody WithdrawDtos.PrepareRequest req) {
        return service.prepare(userId(principal), req);
    }

    private static long userId(AuthPrincipal principal) {
        if (principal == null) {
            throw ApiException.unauthorized("UNAUTHENTICATED", "Authentication required");
        }
        return principal.userId();
    }
}
