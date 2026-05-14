package com.polymarket.tma.market.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record CommentDto(
        String id,
        String body,
        String author,
        @JsonProperty("authorAvatar")
        String authorAvatar,
        Instant createdAt
) {}
