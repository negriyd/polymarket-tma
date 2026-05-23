package com.polymarket.tma.trading.clob;

import com.polymarket.tma.common.ApiException;
import com.polymarket.tma.config.AppProperties;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Calls Polymarket CLOB {@code POST /auth/api-key} with the L1 signed payload to derive
 * {@link ClobCredentials} for the given wallet.
 *
 * <p>Required headers, per Polymarket docs:
 * <ul>
 *     <li>{@code POLY_ADDRESS} — wallet address</li>
 *     <li>{@code POLY_SIGNATURE} — wallet signature of the {@code ClobAuth} EIP-712 typed data</li>
 *     <li>{@code POLY_TIMESTAMP} — unix seconds (same as in the signed payload)</li>
 *     <li>{@code POLY_NONCE} — nonce (same as in the signed payload)</li>
 * </ul>
 */
@Component
public class ClobApiKeyClient {

    private final WebClient client;

    public ClobApiKeyClient(WebClient.Builder builder, AppProperties props) {
        this.client = builder.baseUrl(props.polymarket().clobBaseUrl()).build();
    }

    public Mono<ClobCredentials> deriveKey(String walletAddress,
                                            long timestampSec,
                                            long nonce,
                                            String signature) {
        return client.post()
                .uri("/auth/api-key")
                .header("POLY_ADDRESS", walletAddress)
                .header("POLY_SIGNATURE", signature)
                .header("POLY_TIMESTAMP", Long.toString(timestampSec))
                .header("POLY_NONCE", Long.toString(nonce))
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp -> resp.bodyToMono(String.class).defaultIfEmpty("")
                        .map(body -> {
                            int status = resp.statusCode().value();
                            String safe = body == null ? "" : body;
                            if (status == 403 && safe.toLowerCase().contains("geoblock")) {
                                return ApiException.upstream("CLOB_GEOBLOCKED",
                                        "Polymarket geoblocked the server's region for /auth/api-key. "
                                                + "Redeploy backend to a US datacenter. Upstream: " + safe);
                            }
                            return ApiException.upstream("CLOB_AUTH_" + status,
                                    "CLOB rejected api-key derivation: " + status + " " + safe);
                        }))
                .bodyToMono(ClobCredentials.class);
    }
}
