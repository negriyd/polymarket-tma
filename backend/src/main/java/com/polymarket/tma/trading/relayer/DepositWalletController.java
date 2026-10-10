package com.polymarket.tma.trading.relayer;

import com.polymarket.tma.auth.jwt.AuthPrincipal;
import com.polymarket.tma.common.ApiException;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CLOB v2 deposit-wallet onboarding.
 *
 * <pre>
 *   GET  /api/wallet/deposit-wallet                       -> status (address, deployed, pUSD, approvals)
 *   POST /api/wallet/deposit-wallet/deploy                -> relayer tx (gasless WALLET-CREATE)
 *   POST /api/wallet/deposit-wallet/approvals/prepare     -> EIP-712 Batch to sign with Privy
 *   POST /api/wallet/deposit-wallet/batches/submit        -> relayer tx {batchHash, signature}
 *                                                            (approvals, platform fee, Claim)
 *   GET  /api/wallet/deposit-wallet/transactions/{id}     -> relayer tx progress
 * </pre>
 */
@RestController
@RequestMapping("/api/wallet/deposit-wallet")
public class DepositWalletController {

    private final DepositWalletService service;

    public DepositWalletController(DepositWalletService service) {
        this.service = service;
    }

    @GetMapping
    public DepositWalletDtos.Status status(@AuthenticationPrincipal AuthPrincipal principal) {
        return service.status(userId(principal));
    }

    @PostMapping("/deploy")
    public DepositWalletDtos.RelayerTx deploy(@AuthenticationPrincipal AuthPrincipal principal) {
        return service.deploy(userId(principal));
    }

    @PostMapping("/approvals/prepare")
    public DepositWalletDtos.PreparedBatch prepareApprovals(@AuthenticationPrincipal AuthPrincipal principal) {
        return service.prepareApprovals(userId(principal));
    }

    @PostMapping("/batches/submit")
    public DepositWalletDtos.RelayerTx submitBatch(@AuthenticationPrincipal AuthPrincipal principal,
                                                    @Valid @RequestBody DepositWalletDtos.SubmitBatchRequest req) {
        return service.submitBatch(userId(principal), req);
    }

    @GetMapping("/transactions/{transactionId}")
    public DepositWalletDtos.RelayerTx transaction(@AuthenticationPrincipal AuthPrincipal principal,
                                                    @PathVariable String transactionId) {
        return service.transaction(userId(principal), transactionId);
    }

    private static long userId(AuthPrincipal principal) {
        if (principal == null) {
            throw ApiException.unauthorized("UNAUTHENTICATED", "Authentication required");
        }
        return principal.userId();
    }
}
