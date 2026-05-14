package com.polymarket.tma.market;

import com.polymarket.tma.common.ApiException;
import com.polymarket.tma.config.AppProperties;
import com.polymarket.tma.market.client.ClobClient;
import com.polymarket.tma.market.client.GammaClient;
import com.polymarket.tma.market.dto.CommentDto;
import com.polymarket.tma.market.dto.MarketDto;
import com.polymarket.tma.market.dto.MarketListResponse;
import com.polymarket.tma.market.dto.OrderbookDto;
import com.polymarket.tma.market.dto.PriceHistoryDto;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class MarketService {

    private static final Pattern CONDITION_ID_HEX = Pattern.compile("^0x[a-fA-F0-9]{64}$");

    private final GammaClient gamma;
    private final ClobClient clob;
    private final MarketCacheService cache;
    private final AppProperties props;

    public MarketService(GammaClient gamma, ClobClient clob, MarketCacheService cache, AppProperties props) {
        this.gamma = gamma;
        this.clob = clob;
        this.cache = cache;
        this.props = props;
    }

    public Mono<MarketListResponse> list(int page, int size, String order, boolean ascending, String tag, String search) {
        int safeSize = Math.max(1, Math.min(size, 50));
        int safePage = Math.max(0, page);
        int offset = safePage * safeSize;
        String key = "pm:markets:list:" + safeSize + ":" + offset + ":" + order + ":" + ascending + ":" + tag + ":" + search;

        Mono<List<MarketDto>> loader = gamma.listMarkets(safeSize + 1, offset, order, ascending, tag, search);

        return cache.readThrough(key, List.class, props.polymarket().listCacheTtl(), loader.cast(List.class))
                .map(raw -> {
                    @SuppressWarnings("unchecked")
                    List<MarketDto> markets = (List<MarketDto>) raw;
                    boolean hasMore = markets.size() > safeSize;
                    List<MarketDto> items = hasMore ? markets.subList(0, safeSize) : markets;
                    return new MarketListResponse(items, safePage, safeSize, hasMore);
                });
    }

    public Mono<MarketDto> get(String marketKey) {
        String key = "pm:market:" + marketKey;
        Mono<MarketDto> loader = isHexConditionId(marketKey)
                ? resolveGammaByConditionId(marketKey)
                : gamma.getMarket(marketKey);
        return cache.readThrough(key, MarketDto.class, props.polymarket().detailCacheTtl(), loader)
                .switchIfEmpty(Mono.error(ApiException.notFound("MARKET_NOT_FOUND", "Market not found: " + marketKey)));
    }

    private Mono<MarketDto> resolveGammaByConditionId(String conditionId) {
        return clob.getMarketSlugByConditionId(conditionId)
                .flatMap(slug -> gamma.listMarketsBySlug(1, 0, "volume24hr", false, slug))
                .flatMap(list -> list.stream()
                        .filter(m -> conditionId.equalsIgnoreCase(m.conditionId()))
                        .findFirst()
                        .map(Mono::just)
                        .orElseGet(() ->
                                Mono.error(ApiException.notFound("MARKET_NOT_FOUND", "Market not found: " + conditionId))));
    }

    private static boolean isHexConditionId(String marketKey) {
        return marketKey != null && CONDITION_ID_HEX.matcher(marketKey).matches();
    }

    public Mono<OrderbookDto> orderbook(String tokenId) {
        String key = "pm:book:" + tokenId;
        return cache.readThrough(key, OrderbookDto.class, props.polymarket().orderbookCacheTtl(), clob.getOrderbook(tokenId));
    }

    public Mono<PriceHistoryDto> history(String tokenId, String interval) {
        String safeInterval = interval == null || interval.isBlank() ? "1d" : interval;
        String key = "pm:history:" + tokenId + ":" + safeInterval;
        return cache.readThrough(key, PriceHistoryDto.class, props.polymarket().historyCacheTtl(),
                gamma.getPriceHistory(tokenId, safeInterval));
    }

    /** Polymarket comments are on the parent {@code Event}; resolved via Gamma using the market's condition id. */
    public Mono<List<CommentDto>> comments(String marketKey, int limit, int offset) {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        int safeOffset = Math.max(0, offset);
        return resolveConditionId(marketKey)
                .flatMap(gamma::resolveEventIdForCondition)
                .flatMap(eid -> {
                    try {
                        return gamma.listEventComments(Integer.parseInt(eid), safeLimit, safeOffset, false);
                    } catch (NumberFormatException e) {
                        return Mono.just(Collections.<CommentDto>emptyList());
                    }
                })
                .switchIfEmpty(Mono.just(Collections.emptyList()));
    }

    private Mono<String> resolveConditionId(String marketKey) {
        if (isHexConditionId(marketKey)) {
            return Mono.just(marketKey);
        }
        return get(marketKey).map(MarketDto::conditionId);
    }
}
