package com.polymarket.tma.wallet;

import java.util.List;

public final class ApprovalDtos {

    private ApprovalDtos() {}

    /**
     * Unsigned transaction the wallet should sign and broadcast.
     * {@code value} is always {@code "0x0"} for ERC-20/ERC-1155 approvals.
     */
    public record UnsignedTx(
            String kind,        // "USDC_APPROVE" or "CTF_SET_APPROVAL_FOR_ALL"
            String to,
            String data,
            String value,
            int chainId
    ) {}

    public record AllowanceState(
            String spender,
            String allowance,     // decimal string; null when RPC is unavailable
            boolean approvedForAll,
            Boolean approvedForAllKnown
    ) {}

    public record ApprovalStatus(
            String wallet,
            String spender,
            AllowanceState usdc,
            AllowanceState ctf,
            List<UnsignedTx> missing
    ) {}
}
