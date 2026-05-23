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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
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

    private static final Logger log = LoggerFactory.getLogger(ClobOrderClient.class);

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

    /**
     * Maps a CLOB error response to an {@link ApiException}.
     *
     * <p>Recognises three known classes of structural rejection so the UI / ops layer can
     * distinguish them from generic upstream failures:
     * <ul>
     *     <li><strong>{@code CLOB_GEOBLOCKED}</strong> — 403 "Trading restricted in your region".
     *         The deployment must run in an allowed region (US datacenters of mainstream IaaS
     *         providers work; EU regions, Canada, and Singapore are geoblocked).</li>
     *     <li><strong>{@code CLOB_DEPOSIT_WALLET_REQUIRED}</strong> — 400 "maker address not
     *         allowed, please use the deposit wallet flow". CLOB v2 (post-2026-04-22 migration)
     *         no longer accepts orders signed by raw EOAs: every wallet must first be onboarded
     *         on polymarket.com (deposit + one tiny trade) so its CREATE2 deposit-wallet proxy
     *         is deployed, and subsequent orders use {@code signatureType=3} (POLY_1271) with
     *         {@code maker=proxy}. As of mid-May 2026 the Polymarket SDK has multiple unresolved
     *         issues even after correct onboarding (py-clob-client-v2 #51, #53, #61, #63, #64);
     *         our integration cannot work end-to-end until upstream stabilises.</li>
     *     <li><strong>{@code CLOB_ORDER_VERSION_MISMATCH}</strong> — 400 "order_version_mismatch".
     *         Either {@code OrderBuilder} fell out of sync with the v2 EIP-712 schema, or the
     *         {@code verifyingContract} routing missed a NegRisk market.</li>
     * </ul>
     */
    private static ApiException mapClobError(int status, String body, String operation) {
        String safeBody = body == null ? "" : body;
        String lower = safeBody.toLowerCase();
        if (status == 403 && lower.contains("geoblock")) {
            return ApiException.upstream("CLOB_GEOBLOCKED",
                    "Polymarket has geoblocked this server's region. Redeploy backend "
                            + "to a US datacenter (e.g. DigitalOcean nyc1/nyc3, Vultr EWR/LAX). "
                            + "Upstream: " + safeBody);
        }
        if (status == 400 && lower.contains("deposit wallet flow")) {
            return ApiException.upstream("CLOB_DEPOSIT_WALLET_REQUIRED",
                    "Polymarket CLOB v2 requires this wallet to be onboarded via "
                            + "polymarket.com first: deposit USDC and place at least one small "
                            + "trade through the official UI to deploy your deposit-wallet proxy. "
                            + "Direct EOA trading was deprecated in the 2026-04-22 v2 migration. "
                            + "Tracker: https://github.com/Polymarket/py-clob-client-v2/issues/51. "
                            + "Upstream: " + safeBody);
        }
        if (status == 400 && lower.contains("order_version_mismatch")) {
            return ApiException.upstream("CLOB_ORDER_VERSION_MISMATCH",
                    "Polymarket rejected the EIP-712 order: signed against the wrong CTF "
                            + "Exchange contract. Verify OrderBuilder targets the v2 schema and "
                            + "MarketDto.negRisk routes to the matching verifyingContract. "
                            + "Upstream: " + safeBody);
        }
        return ApiException.upstream("CLOB_" + operation + "_" + status,
                "CLOB rejected " + operation.toLowerCase() + ": " + status + " " + safeBody);
    }

    public Mono<OrderDtos.SubmittedOrderResponse> submit(BuiltOrder built,
                                                          String signature,
                                                          String walletAddress,
                                                          ClobCredentials creds) {
        String body = buildOrderBody(built, signature, creds);

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
                        .map(bodyText -> {
                            // Log the EXACT request body alongside the upstream error so payload-
                            // shape regressions are obvious from `docker logs` without rerunning
                            // anything. Logged at WARN because every 4xx here is a contract miss.
                            log.warn("CLOB rejected POST /order: status={} body={} requestBody={}",
                                    resp.statusCode().value(), bodyText, body);
                            return mapClobError(resp.statusCode().value(), bodyText, "ORDER");
                        }))
                .bodyToMono(Map.class)
                .map(resp -> new OrderDtos.SubmittedOrderResponse(
                        String.valueOf(resp.getOrDefault("orderID", built.orderHash())),
                        String.valueOf(resp.getOrDefault("status", "ACCEPTED")),
                        String.valueOf(resp.getOrDefault("transactionHash", null))));
    }

    /**
     * Serialises the prepared order into the JSON body Polymarket CLOB v2 accepts. Mirrors
     * <a href="https://github.com/Polymarket/py-clob-client-v2/blob/main/py_clob_client_v2/order_utils/model/order_data_v2.py">{@code order_to_json_v2}</a>:
     * <ul>
     *     <li>{@code signature} lives INSIDE the {@code order} object.</li>
     *     <li>{@code side} on the wire is the string {@code "BUY"} / {@code "SELL"} even though
     *         the EIP-712 digest was over uint8 0/1. CLOB reconstructs the numeric form during
     *         signature verification.</li>
     *     <li>{@code salt} and {@code signatureType} are JSON <strong>numbers</strong>, not
     *         strings. Strict type-checking on the validator otherwise rejects the payload as
     *         {@code "Invalid order payload"}.</li>
     *     <li>All other uint256 fields stay as decimal strings (token ids and amounts can exceed
     *         JS {@code Number.MAX_SAFE_INTEGER}; they are big-int strings on Polymarket too).</li>
     *     <li>v2 envelope: top-level {@code owner} is the <strong>API key UUID</strong> (not the
     *         wallet address — that was v1), with {@code deferExec} and {@code postOnly} alongside
     *         {@code orderType}.</li>
     *     <li>v2 order fields removed: {@code taker}, {@code nonce}, {@code feeRateBps}; added:
     *         {@code timestamp} (uint256 ms, in EIP-712 struct), {@code metadata} (bytes32),
     *         {@code builder} (bytes32). {@code expiration} stays on the wire (default {@code "0"})
     *         but is no longer part of the signed digest.</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    private String buildOrderBody(BuiltOrder built, String signature, ClobCredentials creds) {
        Map<String, Object> source = built.wireOrder() != null
                ? built.wireOrder()
                // Defensive fallback: if a cached BuiltOrder predates the v2 schema migration
                // (or if a future caller forgets to populate wireOrder), use the EIP-712 message
                // directly. `expiration` will be filled below via putIfAbsent.
                : (Map<String, Object>) built.typedData().get("message");

        Map<String, Object> order = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : source.entrySet()) {
            order.put(e.getKey(), e.getValue());
        }
        order.putIfAbsent("expiration", "0");
        Object rawSide = order.get("side");
        order.put("side", "0".equals(String.valueOf(rawSide)) ? "BUY" : "SELL");
        order.put("signatureType", parseIntStrict(order.get("signatureType")));
        order.put("salt", parseBigIntStrict(order.get("salt")));
        order.put("signature", signature);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("order", order);
        // py-clob-client-v2: `owner = self.creds.api_key`. Falling back to maker preserves the
        // legacy behaviour when called without creds (tests, unauthenticated scaffold).
        payload.put("owner", creds != null && creds.apiKey() != null ? creds.apiKey() : order.get("maker"));
        payload.put("orderType", "GTC");
        payload.put("deferExec", false);
        payload.put("postOnly", false);

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw ApiException.internal("CLOB_BODY_SERIALIZE", "Failed to serialize order payload");
        }
    }

    private static int parseIntStrict(Object v) {
        if (v instanceof Number n) return n.intValue();
        return Integer.parseInt(String.valueOf(v));
    }

    /**
     * Returns a {@link java.math.BigInteger} so Jackson emits a JSON number even for 96-bit salts;
     * a plain {@code long} would overflow. Polymarket validates {@code salt} as numeric.
     */
    private static java.math.BigInteger parseBigIntStrict(Object v) {
        if (v instanceof java.math.BigInteger b) return b;
        if (v instanceof Number n) return java.math.BigInteger.valueOf(n.longValue());
        return new java.math.BigInteger(String.valueOf(v));
    }

    /**
     * Cancel a previously submitted CLOB order.
     *
     * <p>Mirrors py-clob-client {@code cancel(order_id)}: HTTP {@code DELETE /order} with body
     * {@code {"orderID": "..."}} signed by L2 HMAC headers. Without {@code creds} the request can
     * never succeed, so we short-circuit with a structured {@code CLOB_AUTH_REQUIRED} error.
     */
    public Mono<Void> cancel(String orderId, String walletAddress, ClobCredentials creds) {
        if (orderId == null || orderId.isBlank()) {
            throw ApiException.badRequest("ORDER_ID_REQUIRED", "orderId is required");
        }
        if (creds == null) {
            throw ApiException.badRequest("CLOB_AUTH_REQUIRED",
                    "Connect Polymarket trading first (POST /api/clob/auth/prepare → submit)");
        }
        if (walletAddress == null || walletAddress.isBlank()) {
            throw ApiException.badRequest("WALLET_REQUIRED", "Wallet address is required");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderID", orderId);

        String body;
        try {
            body = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw ApiException.internal("CLOB_BODY_SERIALIZE", "Failed to serialize cancel payload");
        }

        ClobL2Signer.Signed signed = l2Signer.sign(creds, "DELETE", ORDER_PATH, body);
        return client.method(HttpMethod.DELETE)
                .uri(ORDER_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .header("POLY_ADDRESS", walletAddress)
                .header("POLY_API_KEY", creds.apiKey())
                .header("POLY_PASSPHRASE", creds.passphrase())
                .header("POLY_TIMESTAMP", Long.toString(signed.timestampSec()))
                .header("POLY_SIGNATURE", signed.signature())
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp -> resp.bodyToMono(String.class).defaultIfEmpty("")
                        .map(bodyText -> mapClobError(resp.statusCode().value(), bodyText, "CANCEL")))
                .toBodilessEntity()
                .doOnSuccess(r -> log.info("Cancelled CLOB order {} for {}", orderId, walletAddress))
                .then();
    }
}
