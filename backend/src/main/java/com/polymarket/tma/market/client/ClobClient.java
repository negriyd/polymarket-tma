package com.polymarket.tma.market.client;

import com.polymarket.tma.common.ApiException;
import com.polymarket.tma.config.AppProperties;
import com.polymarket.tma.market.dto.OrderbookDto;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

@Component
public class ClobClient {

    private static final Logger log = LoggerFactory.getLogger(ClobClient.class);

    private final WebClient client;

    public ClobClient(WebClient.Builder builder, AppProperties props) {
        this.client = builder.baseUrl(props.polymarket().clobBaseUrl()).build();
    }

    public Mono<OrderbookDto> getOrderbook(String tokenId) {
        return client.get()
                .uri(uri -> uri.path("/book").queryParam("token_id", tokenId).build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp -> resp.bodyToMono(String.class).defaultIfEmpty("")
                        .map(body -> ApiException.upstream("CLOB_HTTP_" + resp.statusCode().value(),
                                "CLOB error: " + resp.statusCode().value() + " " + body)))
                .bodyToMono(OrderbookDto.class)
                .retryWhen(Retry.backoff(2, Duration.ofMillis(200))
                        .maxBackoff(Duration.ofSeconds(2))
                        .jitter(0.5)
                        .filter(t -> t instanceof ApiException api
                                && (api.getCode().startsWith("CLOB_HTTP_5") || api.getCode().equals("CLOB_HTTP_429"))))
                .doOnError(e -> log.warn("CLOB orderbook({}) failed: {}", tokenId, e.toString()));
    }
}
