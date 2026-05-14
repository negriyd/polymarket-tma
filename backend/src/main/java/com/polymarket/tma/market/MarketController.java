package com.polymarket.tma.market;

import com.polymarket.tma.market.dto.CommentDto;
import com.polymarket.tma.market.dto.MarketDto;
import com.polymarket.tma.market.dto.MarketListResponse;
import com.polymarket.tma.market.dto.OrderbookDto;
import com.polymarket.tma.market.dto.PriceHistoryDto;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/markets")
public class MarketController {

    private final MarketService service;

    public MarketController(MarketService service) {
        this.service = service;
    }

    @GetMapping
    public Mono<MarketListResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false, defaultValue = "volume24hr") String order,
            @RequestParam(required = false, defaultValue = "false") boolean ascending,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String search) {
        return service.list(page, size, order, ascending, tag, search);
    }

    @GetMapping("/{conditionId}")
    public Mono<MarketDto> get(@PathVariable String conditionId) {
        return service.get(conditionId);
    }

    @GetMapping("/{marketKey}/comments")
    public Mono<List<CommentDto>> comments(
            @PathVariable String marketKey,
            @RequestParam(defaultValue = "30") int size,
            @RequestParam(defaultValue = "0") int page) {
        return service.comments(marketKey, size, page * size);
    }

    @GetMapping("/{conditionId}/orderbook")
    public Mono<OrderbookDto> orderbook(@PathVariable String conditionId,
                                        @RequestParam(name = "token_id") String tokenId) {
        return service.orderbook(tokenId);
    }

    @GetMapping("/{conditionId}/history")
    public Mono<PriceHistoryDto> history(@PathVariable String conditionId,
                                         @RequestParam(name = "token_id") String tokenId,
                                         @RequestParam(defaultValue = "1d") String interval) {
        return service.history(tokenId, interval);
    }
}
