package com.polymarket.tma.trading.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public final class ClobAuthDtos {

    private ClobAuthDtos() {}

    /** L1 prepare response. Wallet signs {@code typedData}; client echoes the values back on submit. */
    public record PrepareResponse(
            String address,
            long timestamp,
            long nonce,
            String digestHex,
            Map<String, Object> typedData
    ) {}

    public record SubmitRequest(
            @NotBlank String signature,
            long timestamp,
            long nonce
    ) {}

    public record StatusResponse(boolean configured) {}
}
