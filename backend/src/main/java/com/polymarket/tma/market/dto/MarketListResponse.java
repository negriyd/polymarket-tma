package com.polymarket.tma.market.dto;

import java.util.List;

public record MarketListResponse(
        List<MarketDto> items,
        int page,
        int size,
        boolean hasMore
) {
}
