package com.polymarket.tma.redeem;

import com.polymarket.tma.auth.jwt.AuthPrincipal;
import com.polymarket.tma.common.ApiException;
import com.polymarket.tma.trading.relayer.DepositWalletDtos;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Surfaces the redeem-positions calldata for a settled market.
 *
 * <pre>
 *   POST /api/positions/redeem/prepare {conditionId, outcomeIndex}
 *   -> { conditionId, outcomeIndex, indexSets, tx: {to, data, value, chainId} }
 * </pre>
 *
 * The frontend takes {@code tx} and broadcasts it via Privy. After confirmation the user's USDC
 * balance reflects the payout for the winning outcome (zero if the index set lost).
 *
 * <pre>
 *   POST /api/positions/redeem/prepare-batch {conditionId, negRisk}
 *   -> deposit-wallet Batch to sign; submit via /api/wallet/deposit-wallet/batches/submit
 * </pre>
 * The batch variant is the one for CLOB v2 positions (held by the deposit wallet, paid in pUSD).
 */
@RestController
@RequestMapping("/api/positions/redeem")
public class RedeemController {

    private final RedeemService service;

    public RedeemController(RedeemService service) {
        this.service = service;
    }

    @PostMapping("/prepare")
    public RedeemDtos.PrepareResponse prepare(@AuthenticationPrincipal AuthPrincipal principal,
                                               @Valid @RequestBody RedeemDtos.PrepareRequest req) {
        if (principal == null) {
            throw ApiException.unauthorized("UNAUTHENTICATED", "Authentication required");
        }
        return service.prepare(principal.userId(), req);
    }

    @PostMapping("/prepare-batch")
    public DepositWalletDtos.PreparedBatch prepareBatch(@AuthenticationPrincipal AuthPrincipal principal,
                                                        @Valid @RequestBody RedeemDtos.BatchRequest req) {
        if (principal == null) {
            throw ApiException.unauthorized("UNAUTHENTICATED", "Authentication required");
        }
        return service.prepareBatch(principal.userId(), req);
    }
}
