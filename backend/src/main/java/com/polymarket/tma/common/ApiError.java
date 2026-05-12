package com.polymarket.tma.common;

import java.time.Instant;

public record ApiError(
        String code,
        String message,
        Instant timestamp,
        String traceId
) {
    public static ApiError of(String code, String message, String traceId) {
        return new ApiError(code, message, Instant.now(), traceId);
    }
}
