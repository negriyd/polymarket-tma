package com.polymarket.tma.trading;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.polymarket.tma.common.ApiException;
import com.polymarket.tma.config.AppProperties;
import com.polymarket.tma.market.MarketCacheService;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Reads a user's open positions from the Polymarket Data API.
 *
 * <p>The endpoint {@code GET https://data-api.polymarket.com/positions?user=&lt;wallet&gt;} returns one
 * record per outcome the user holds. The schema below mirrors the public API (camelCase) and matches the
 * fields used by official clients (py-clob-client, polymarket-rs-sdk). Unknown fields are tolerated so
 * minor upstream additions do not break us.
 */
@Component
public class PositionsClient {

    private static final Duration CACHE_TTL = Duration.ofSeconds(10);

    private final WebClient client;
    private final MarketCacheService cache;

    public PositionsClient(WebClient.Builder builder, AppProperties props, MarketCacheService cache) {
        this.client = builder.baseUrl(props.polymarket().dataBaseUrl()).build();
        this.cache = cache;
    }

    public Mono<List<Position>> getPositions(String wallet) {
        String key = "pm:positions:" + wallet.toLowerCase();
        Mono<List<Position>> loader = client.get()
                .uri(uri -> uri.path("/positions").queryParam("user", wallet).build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp -> resp.bodyToMono(String.class).defaultIfEmpty("")
                        .map(body -> ApiException.upstream("DATA_HTTP_" + resp.statusCode().value(),
                                "Data API error: " + resp.statusCode().value() + " " + body)))
                .bodyToMono(new ParameterizedTypeReference<List<Position>>() {});
        return cache.readThrough(key, List.class, CACHE_TTL, loader.cast(List.class))
                .map(raw -> {
                    @SuppressWarnings("unchecked")
                    List<Position> positions = (List<Position>) raw;
                    return positions;
                });
    }

    /**
     * Single position as returned by Polymarket Data API. {@code favorite} is added on the way out by
     * {@link PositionsController} based on the caller's saved markets — Data API itself never sets it.
     *
     * <p>All numeric quantities use {@link BigDecimal} so exact USDC math stays untruncated. Most flags
     * are non-null primitives because the API consistently returns them; a missing field will default to
     * {@code false} or {@code 0} which is the expected fallback.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Position(
            String proxyWallet,
            String asset,
            String conditionId,
            BigDecimal size,
            BigDecimal avgPrice,
            BigDecimal initialValue,
            BigDecimal currentValue,
            BigDecimal cashPnl,
            BigDecimal percentPnl,
            BigDecimal totalBought,
            BigDecimal realizedPnl,
            BigDecimal percentRealizedPnl,
            BigDecimal curPrice,
            boolean redeemable,
            boolean mergeable,
            String title,
            String slug,
            String icon,
            String eventSlug,
            String outcome,
            int outcomeIndex,
            String oppositeOutcome,
            String oppositeAsset,
            String endDate,
            boolean negativeRisk,
            /** Filled in by our backend, not by the upstream API. */
            Boolean favorite
    ) {

        /** Returns a copy with {@code favorite} replaced. */
        public Position withFavorite(boolean fav) {
            return new Position(
                    proxyWallet, asset, conditionId, size, avgPrice, initialValue, currentValue,
                    cashPnl, percentPnl, totalBought, realizedPnl, percentRealizedPnl, curPrice,
                    redeemable, mergeable, title, slug, icon, eventSlug, outcome, outcomeIndex,
                    oppositeOutcome, oppositeAsset, endDate, negativeRisk, fav);
        }
    }
}
