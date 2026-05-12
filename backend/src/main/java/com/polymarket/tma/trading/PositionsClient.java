package com.polymarket.tma.trading;

import com.polymarket.tma.common.ApiException;
import com.polymarket.tma.config.AppProperties;
import com.polymarket.tma.market.MarketCacheService;
import java.time.Duration;
import java.util.List;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

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

    public record Position(
            String conditionId,
            String tokenId,
            String outcome,
            java.math.BigDecimal size,
            java.math.BigDecimal avgPrice,
            java.math.BigDecimal currentValue,
            java.math.BigDecimal pnl
    ) {}
}
