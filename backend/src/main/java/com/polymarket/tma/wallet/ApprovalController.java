package com.polymarket.tma.wallet;

import com.polymarket.tma.auth.jwt.AuthPrincipal;
import com.polymarket.tma.common.ApiException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes pre-trade approval state and unsigned transactions for the wallet to sign.
 *
 * <pre>
 *   GET /api/wallet/approvals  -> { usdc, ctf, missing: [{kind, to, data, value, chainId}] }
 * </pre>
 *
 * The frontend submits each {@code missing} entry as a regular tx via Privy. After all txs land,
 * subsequent calls should return an empty {@code missing} list and orders can be placed.
 */
@RestController
@RequestMapping("/api/wallet/approvals")
public class ApprovalController {

    private final ApprovalService service;

    public ApprovalController(ApprovalService service) {
        this.service = service;
    }

    @GetMapping
    public ApprovalDtos.ApprovalStatus status(@AuthenticationPrincipal AuthPrincipal principal) {
        if (principal == null) {
            throw ApiException.unauthorized("UNAUTHENTICATED", "Authentication required");
        }
        return service.status(principal.userId());
    }
}
