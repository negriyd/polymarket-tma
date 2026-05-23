package com.polymarket.tma.redeem;

import com.polymarket.tma.wallet.ApprovalDtos;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public final class RedeemDtos {

    private RedeemDtos() {}

    /** Client either passes the explicit outcome index (preferred) or a list of bitmasks. */
    public record PrepareRequest(
            @NotBlank String conditionId,
            /** Outcome index in the market (0=YES, 1=NO for binary). Used to derive the index set. */
            @NotNull @Min(0) Integer outcomeIndex
    ) {}

    /**
     * Unsigned tx + the inputs that produced it. The frontend signs and broadcasts via Privy.
     *
     * @param indexSets   bitmask(s) burned in this redemption (single entry for the chosen outcome).
     */
    public record PrepareResponse(
            String conditionId,
            int outcomeIndex,
            List<String> indexSets,
            ApprovalDtos.UnsignedTx tx
    ) {}
}
