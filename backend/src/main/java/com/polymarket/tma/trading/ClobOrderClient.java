package com.polymarket.tma.trading;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polymarket.tma.common.ApiException;
import com.polymarket.tma.config.AppProperties;
import com.polymarket.tma.trading.OrderBuilder.BuiltOrder;
import com.polymarket.tma.trading.clob.ClobCredentials;
import com.polymarket.tma.trading.clob.ClobL2Signer;
import com.polymarket.tma.trading.dto.OrderDtos;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Submits signed orders to Polymarket CLOB.
 *
 * <p>L1 (key derivation via {@link com.polymarket.tma.trading.clob.ClobAuthService}) and L2 (HMAC headers
 * via {@link ClobL2Signer}) are both wired here. If the user has no cached credentials the request still
 * goes through without {@code POLY_*} headers so the existing scaffold contract is preserved for tests
 * and unauthenticated CLOB endpoints; production must call {@code /api/clob/auth/...} first.
 */
@Component
public class ClobOrderClient {

    private static final String ORDER_PATH = "/order";

    private final WebClient client;
    private final ClobL2Signer l2Signer;
    private final ObjectMapper objectMapper;

    public ClobOrderClient(WebClient.Builder builder,
                           AppProperties props,
                           ClobL2Signer l2Signer,
                           ObjectMapper objectMapper) {
        this.client = builder.baseUrl(props.polymarket().clobBaseUrl()).build();
        this.l2Signer = l2Signer;
        this.objectMapper = objectMapper;
    }

    public Mono<OrderDtos.SubmittedOrderResponse> submit(BuiltOrder built,
                                                          String signature,
                                                          String walletAddress,
                                                          ClobCredentials creds) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("order", built.typedData().get("message"));
        payload.put("signature", signature);
        payload.put("orderType", "GTC");
        payload.put("owner", ((Map<?, ?>) built.typedData().get("message")).get("maker"));

        String body;
        try {
            body = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw ApiException.internal("CLOB_BODY_SERIALIZE", "Failed to serialize order payload");
        }

        WebClient.RequestBodySpec spec = client.post()
                .uri(ORDER_PATH)
                .contentType(MediaType.APPLICATION_JSON);

        if (creds != null && walletAddress != null) {
            ClobL2Signer.Signed signed = l2Signer.sign(creds, "POST", ORDER_PATH, body);
            spec = spec
                    .header("POLY_ADDRESS", walletAddress)
                    .header("POLY_API_KEY", creds.apiKey())
                    .header("POLY_PASSPHRASE", creds.passphrase())
                    .header("POLY_TIMESTAMP", Long.toString(signed.timestampSec()))
                    .header("POLY_SIGNATURE", signed.signature());
        }

        return spec
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp -> resp.bodyToMono(String.class).defaultIfEmpty("")
                        .map(bodyText -> ApiException.upstream("CLOB_ORDER_" + resp.statusCode().value(),
                                "CLOB rejected order: " + resp.statusCode().value() + " " + bodyText)))
                .bodyToMono(Map.class)
                .map(resp -> new OrderDtos.SubmittedOrderResponse(
                        String.valueOf(resp.getOrDefault("orderID", built.orderHash())),
                        String.valueOf(resp.getOrDefault("status", "ACCEPTED")),
                        String.valueOf(resp.getOrDefault("transactionHash", null))));
    }
}
