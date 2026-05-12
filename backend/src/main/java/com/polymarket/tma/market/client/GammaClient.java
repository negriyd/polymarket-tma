package com.polymarket.tma.market.client;

import com.polymarket.tma.common.ApiException;
import com.polymarket.tma.config.AppProperties;
import com.polymarket.tma.market.dto.MarketDto;
import com.polymarket.tma.market.dto.PriceHistoryDto;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

@Component
public class GammaClient {

    private static final Logger log = LoggerFactory.getLogger(GammaClient.class);

    private final WebClient client;
    private final MeterRegistry meters;

    @Autowired
    public GammaClient(WebClient.Builder builder, AppProperties props, MeterRegistry meters) {
        this.client = builder.baseUrl(props.polymarket().gammaBaseUrl()).build();
        this.meters = meters;
    }

    /** Backwards-compatible ctor used by tests that do not bring up a MeterRegistry. */
    public GammaClient(WebClient.Builder builder, AppProperties props) {
        this(builder, props, new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
    }

    public Mono<List<MarketDto>> listMarkets(int limit, int offset, String order, boolean ascending, String tag, String search) {
        return listMarkets(limit, offset, order, ascending, tag, search, null);
    }

    public Mono<List<MarketDto>> listMarkets(int limit, int offset, String order, boolean ascending,
                                            String tag, String search, String slug) {
        return client.get()
                .uri(uri -> buildListUri(uri, limit, offset, order, ascending, tag, search, slug))
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::mapError)
                .bodyToMono(new ParameterizedTypeReference<List<MarketDto>>() {})
                .retryWhen(retrySpec("listMarkets"))
                .doOnError(e -> log.warn("Gamma listMarkets failed: {}", e.toString()));
    }

    public Mono<MarketDto> getMarket(String gammaMarketId) {
        return client.get()
                .uri(uri -> uri.path("/markets/{id}").build(gammaMarketId))
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::mapError)
                .bodyToMono(MarketDto.class)
                .retryWhen(retrySpec("getMarket"))
                .doOnError(e -> log.warn("Gamma getMarket({}) failed: {}", gammaMarketId, e.toString()));
    }

    public Mono<PriceHistoryDto> getPriceHistory(String tokenId, String interval) {
        return client.get()
                .uri(uri -> uri.path("/prices-history")
                        .queryParam("market", tokenId)
                        .queryParam("interval", interval)
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::mapError)
                .bodyToMono(PriceHistoryDto.class)
                .retryWhen(retrySpec("getPriceHistory"))
                .doOnError(e -> log.warn("Gamma getPriceHistory({}) failed: {}", tokenId, e.toString()));
    }

    private static java.net.URI buildListUri(UriBuilder uri, int limit, int offset, String order,
                                             boolean ascending, String tag, String search, String slug) {
        uri.path("/markets")
                .queryParam("limit", limit)
                .queryParam("offset", offset)
                .queryParam("active", true)
                .queryParam("closed", false)
                .queryParam("order", order == null ? "volume24hr" : order)
                .queryParam("ascending", ascending);
        if (tag != null && !tag.isBlank()) {
            uri.queryParam("tag_id", tag);
        }
        if (search != null && !search.isBlank()) {
            uri.queryParam("search", search);
        }
        if (slug != null && !slug.isBlank()) {
            uri.queryParam("slug", slug);
        }
        return uri.build();
    }

    private Mono<? extends Throwable> mapError(org.springframework.web.reactive.function.client.ClientResponse resp) {
        meters.counter("polymarket_upstream_errors_total",
                "upstream", "gamma",
                "status", Integer.toString(resp.statusCode().value()))
                .increment();
        return resp.bodyToMono(String.class).defaultIfEmpty("").map(body ->
                ApiException.upstream("GAMMA_HTTP_" + resp.statusCode().value(),
                        "Gamma API error: " + resp.statusCode().value() + " " + body));
    }

    private Retry retrySpec(String op) {
        return Retry.backoff(2, Duration.ofMillis(200))
                .maxBackoff(Duration.ofSeconds(2))
                .jitter(0.5)
                .filter(this::isRetryable)
                .doBeforeRetry(s -> log.info("Retrying {} attempt {}", op, s.totalRetries() + 1));
    }

    private boolean isRetryable(Throwable t) {
        if (t instanceof ApiException api) {
            return api.getCode().startsWith("GAMMA_HTTP_5") || api.getCode().equals("GAMMA_HTTP_429");
        }
        return true;
    }
}
