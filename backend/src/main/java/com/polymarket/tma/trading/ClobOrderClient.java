package com.polymarket.tma.trading;

import com.polymarket.tma.common.ApiException;
import com.polymarket.tma.config.AppProperties;
import com.polymarket.tma.trading.OrderBuilder.BuiltOrder;
import com.polymarket.tma.trading.dto.OrderDtos;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Submits signed orders to Polymarket CLOB. The full production flow requires two layers of auth:
 *
 * <ul>
 *   <li>L1: derive API credentials by signing an EIP-712 payload with the user's wallet.</li>
 *   <li>L2: every request must be signed with an HMAC over {@code timestamp + method + path + body} using
 *       the derived API secret, plus an L2 nonce.</li>
 * </ul>
 *
 * <p>For the MVP scaffold this client posts the order JSON as-is with the EIP-712 signature; expanding to L1/L2
 * is left as a focused follow-up (see {@code docs/trading.md}). The interface is stable so callers do not change.
 */
@Component
public class ClobOrderClient {

    private final WebClient client;

    public ClobOrderClient(WebClient.Builder builder, AppProperties props) {
        this.client = builder.baseUrl(props.polymarket().clobBaseUrl()).build();
    }

    public Mono<OrderDtos.SubmittedOrderResponse> submit(BuiltOrder built, String signature) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("order", built.typedData().get("message"));
        payload.put("signature", signature);
        payload.put("orderType", "GTC");
        payload.put("owner", ((Map<?, ?>) built.typedData().get("message")).get("maker"));
        return client.post()
                .uri("/order")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp -> resp.bodyToMono(String.class).defaultIfEmpty("")
                        .map(body -> ApiException.upstream("CLOB_ORDER_" + resp.statusCode().value(),
                                "CLOB rejected order: " + resp.statusCode().value() + " " + body)))
                .bodyToMono(Map.class)
                .map(body -> new OrderDtos.SubmittedOrderResponse(
                        String.valueOf(body.getOrDefault("orderID", built.orderHash())),
                        String.valueOf(body.getOrDefault("status", "ACCEPTED")),
                        String.valueOf(body.getOrDefault("transactionHash", null))));
    }
}
