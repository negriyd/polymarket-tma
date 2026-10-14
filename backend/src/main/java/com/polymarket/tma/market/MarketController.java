package com.polymarket.tma.market;

import com.polymarket.tma.market.i18n.MarketLocale;
import com.polymarket.tma.market.i18n.MarketTranslationService;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.RequestHeader;

import com.polymarket.tma.market.dto.CommentDto;
import com.polymarket.tma.market.dto.EventDto;
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
    private final MarketTranslationService translations;

    public MarketController(MarketService service, MarketTranslationService translations) {
        this.service = service;
        this.translations = translations;
    }

    @GetMapping
    public Mono<MarketListResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false, defaultValue = "volume24hr") String order,
            @RequestParam(required = false, defaultValue = "false") boolean ascending,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String search,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String language) {
        String locale = MarketLocale.fromHeader(language);
        return service.list(page, size, order, ascending, tag, search)
                .flatMap(resp -> translations.translateMarkets(resp.items(), locale)
                        .map(items -> new MarketListResponse(items, resp.page(), resp.size(), resp.hasMore())));
    }

    /**
     * Home's "about your region": the busiest open markets tagged with the places the request's
     * language is spoken; empty for languages without such tags.
     */
    @GetMapping("/local")
    public Mono<List<MarketDto>> local(
            @RequestParam(defaultValue = "6") int size,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String language) {
        String locale = MarketLocale.fromHeader(language);
        List<String> tags = LocalTopics.tagsFor(locale);
        if (tags.isEmpty()) {
            return Mono.just(List.of());
        }
        int n = Math.max(1, Math.min(size, 12));
        return reactor.core.publisher.Flux.fromIterable(tags)
                .concatMap(tag -> service.list(0, n, "volume24hr", false, tag, null)
                        .map(MarketListResponse::items)
                        .onErrorReturn(List.of()))
                .collectList()
                .map(lists -> mergeByVolume(lists, n))
                .flatMap(items -> translations.translateMarkets(items, locale));
    }

    static List<MarketDto> mergeByVolume(List<List<MarketDto>> lists, int n) {
        java.util.Map<String, MarketDto> unique = new java.util.LinkedHashMap<>();
        lists.forEach(l -> l.forEach(m -> unique.putIfAbsent(m.conditionId(), m)));
        return unique.values().stream()
                .sorted(java.util.Comparator.comparing(
                        (MarketDto m) -> m.volume24h() == null ? java.math.BigDecimal.ZERO : m.volume24h()).reversed())
                .limit(n)
                .toList();
    }

    @GetMapping("/{conditionId}")
    public Mono<MarketDto> get(@PathVariable String conditionId,
                               @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String language) {
        return service.get(conditionId)
                .flatMap(m -> translations.translateMarket(m, MarketLocale.fromHeader(language)));
    }

    /** Sibling markets of this market's event (other dates or candidates), for choosing which to trade. */
    @GetMapping("/{marketKey}/event")
    public Mono<EventDto> event(@PathVariable String marketKey,
                                @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String language) {
        return service.event(marketKey, MarketLocale.fromHeader(language));
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
