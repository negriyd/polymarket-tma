package com.polymarket.tma.trading;

import static org.assertj.core.api.Assertions.assertThat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.polymarket.tma.common.ApiException;
import com.polymarket.tma.config.AppProperties;
import com.polymarket.tma.config.WebClientConfig;
import com.polymarket.tma.trading.OrderBuilder.BuiltOrder;
import com.polymarket.tma.trading.clob.ClobCredentials;
import com.polymarket.tma.trading.clob.ClobL2Signer;
import java.math.BigInteger;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Regression coverage for the JSON shape of {@code POST /order} against Polymarket CLOB v2.
 *
 * <p>The schema migrated on 2026-04-22; old v1 payloads return {@code 400
 * "order_version_mismatch"}. v2 requirements verified here:
 * <ul>
 *     <li>{@code signature} is embedded INSIDE the {@code order} object.</li>
 *     <li>{@code side} on the wire is the string {@code "BUY"} / {@code "SELL"} (the EIP-712
 *         digest itself was over the numeric uint8 0/1 — CLOB reconstructs it server-side).</li>
 *     <li>{@code salt} and {@code signatureType} are JSON <strong>numbers</strong>; uint256
 *         amounts/tokenIds/timestamps stay as strings to avoid float64 precision loss.</li>
 *     <li>v2 envelope: {@code owner} is the API key UUID (was the wallet in v1); top-level
 *         {@code deferExec} and {@code postOnly} sit alongside {@code orderType}.</li>
 *     <li>v2 order has {@code timestamp} (in EIP-712 struct), {@code metadata}, {@code builder},
 *         and {@code expiration} (default {@code "0"}, API-only — NOT signed).</li>
 *     <li>v1-only fields are absent: {@code nonce}, {@code feeRateBps}, {@code taker}.</li>
 * </ul>
 * Source of truth: <a href="https://github.com/Polymarket/py-clob-client-v2/blob/main/py_clob_client_v2/order_utils/model/order_data_v2.py">{@code py_clob_client_v2/order_data_v2.py}</a>.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClobOrderClientPayloadTest {

    private static final String BYTES32_ZERO = "0x" + "0".repeat(64);

    private WireMockServer wm;
    private ClobOrderClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeAll
    void setUp() {
        wm = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wm.start();

        AppProperties props = new AppProperties(
                new AppProperties.Cors(List.of("*")),
                new AppProperties.Jwt("0123456789012345678901234567890123",
                        Duration.ofMinutes(15), Duration.ofDays(30), "test"),
                new AppProperties.Telegram("test", Duration.ofHours(24)),
                new AppProperties.Polymarket(
                        "http://localhost:" + wm.port(),
                        "http://localhost:" + wm.port(),
                        "http://localhost:" + wm.port(),
                        "ws://localhost:" + wm.port(),
                        Duration.ofSeconds(30), Duration.ofSeconds(5),
                        Duration.ofSeconds(3), Duration.ofMinutes(2)),
                new AppProperties.Polygon("", "", "", "", ""),
                new AppProperties.Privy("", ""),
                new AppProperties.Fees(0, ""));

        client = new ClobOrderClient(
                new WebClientConfig().webClientBuilder(),
                props,
                new ClobL2Signer(),
                mapper);
    }

    @AfterAll
    void tearDown() {
        wm.stop();
    }

    /**
     * Each test starts from the same accepting-CLOB stub. Tests that want to assert error mapping
     * override the stub for their own scope without polluting siblings.
     */
    @BeforeEach
    void resetStubs() {
        wm.resetAll();
        wm.stubFor(WireMock.post("/order")
                .willReturn(WireMock.okJson(
                        "{\"orderID\":\"0xabc\",\"status\":\"ACCEPTED\",\"transactionHash\":null}")));
    }

    @Test
    void buyOrderEncodesV2WireFormat() throws Exception {
        BuiltOrder built = makeBuiltOrder(/* side */ 0);
        ClobCredentials creds = new ClobCredentials("api-key-uuid", base64("secret-key-32-bytes-padding-pad="), "passph");

        client.submit(built, "0xdeadbeef" + "01".repeat(64), "0xWALLET", creds).block();

        String received = wm.findAll(WireMock.postRequestedFor(WireMock.urlEqualTo("/order")))
                .get(0)
                .getBodyAsString();
        JsonNode root = mapper.readTree(received);

        // v2 envelope
        assertThat(root.path("owner").asText())
                .as("v2 owner is the API key UUID, not the wallet (was wallet in v1)")
                .isEqualTo("api-key-uuid");
        assertThat(root.path("orderType").asText()).isEqualTo("GTC");
        assertThat(root.path("deferExec").isBoolean()).as("v2 deferExec is at top level").isTrue();
        assertThat(root.path("deferExec").asBoolean()).isFalse();
        assertThat(root.path("postOnly").isBoolean()).as("v2 postOnly is at top level").isTrue();
        assertThat(root.path("postOnly").asBoolean()).isFalse();

        // signature placement
        assertThat(root.has("signature")).as("signature must NOT be at top level").isFalse();
        assertThat(root.path("order").path("signature").asText())
                .as("signature must live inside `order` (matches py-/ts-clob-client v2)")
                .startsWith("0xdeadbeef");

        // side / salt / signatureType wire types
        assertThat(root.path("order").path("side").asText())
                .as("side must be the string \"BUY\"/\"SELL\" on the wire")
                .isEqualTo("BUY");
        assertThat(root.path("order").path("signatureType").isNumber())
                .as("signatureType must be a JSON number (not a string)")
                .isTrue();
        assertThat(root.path("order").path("signatureType").asInt()).isEqualTo(0);
        assertThat(root.path("order").path("salt").isNumber())
                .as("salt must be a JSON number (not a string) — and < 2^53")
                .isTrue();

        // big-int amounts/tokenIds stay as strings (would overflow JSON Number for 96-bit values)
        assertThat(root.path("order").path("makerAmount").isTextual()).isTrue();
        assertThat(root.path("order").path("tokenId").isTextual()).isTrue();
        assertThat(root.path("order").path("timestamp").isTextual())
                .as("v2 timestamp is a uint256 ms string")
                .isTrue();

        // v2-only fields
        assertThat(root.path("order").path("metadata").asText()).isEqualTo(BYTES32_ZERO);
        assertThat(root.path("order").path("builder").asText()).isEqualTo(BYTES32_ZERO);
        assertThat(root.path("order").path("expiration").asText())
                .as("expiration is API-only in v2 with default \"0\"")
                .isEqualTo("0");

        // v1-only fields must be GONE
        assertThat(root.path("order").has("nonce")).as("v1 `nonce` removed").isFalse();
        assertThat(root.path("order").has("feeRateBps")).as("v1 `feeRateBps` removed").isFalse();
        assertThat(root.path("order").has("taker")).as("v1 `taker` removed").isFalse();
    }

    /**
     * Polymarket CLOB v2 (post-2026-04-22 migration) deprecated direct EOA trading. Fresh wallets
     * that haven't onboarded via polymarket.com get this 400 — surface a structured error code so
     * the UI can render a clear "deposit + one trade on polymarket.com first" message instead of
     * a raw upstream blob. See py-clob-client-v2 issues #51 / #53 / #61 / #63 for the upstream
     * tracker.
     */
    @Test
    void mapsDepositWalletRejectionToStructuredCode() {
        // Override the default OK stub so submit hits a 400 with the deposit-wallet body.
        wm.stubFor(WireMock.post("/order")
                .willReturn(WireMock.aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"maker address not allowed, please use the deposit wallet flow\"}")));

        BuiltOrder built = makeBuiltOrder(0);
        ClobCredentials creds = new ClobCredentials("api-key-uuid", base64("secret-key-32-bytes-padding-pad="), "passph");

        assertThatThrownBy(() -> client.submit(built, "0xfeed", "0xWALLET", creds).block())
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException api = (ApiException) e;
                    assertThat(api.getCode()).isEqualTo("CLOB_DEPOSIT_WALLET_REQUIRED");
                    assertThat(api.getMessage()).contains("polymarket.com");
                });
    }

    @Test
    void mapsOrderVersionMismatchToStructuredCode() {
        wm.stubFor(WireMock.post("/order")
                .willReturn(WireMock.aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"order_version_mismatch\"}")));

        BuiltOrder built = makeBuiltOrder(0);
        ClobCredentials creds = new ClobCredentials("api-key", base64("secret-key-32-bytes-padding-pad="), "passph");

        assertThatThrownBy(() -> client.submit(built, "0xfeed", "0xWALLET", creds).block())
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode())
                        .isEqualTo("CLOB_ORDER_VERSION_MISMATCH"));
    }

    @Test
    void sellOrderEncodesSideAsString() throws Exception {
        BuiltOrder built = makeBuiltOrder(/* side */ 1);
        ClobCredentials creds = new ClobCredentials("api-key-uuid", base64("secret-key-32-bytes-padding-pad="), "passph");

        client.submit(built, "0xfeed", "0xWALLET", creds).block();

        var calls = wm.findAll(WireMock.postRequestedFor(WireMock.urlEqualTo("/order")));
        String received = calls.get(calls.size() - 1).getBodyAsString();
        JsonNode root = mapper.readTree(received);

        assertThat(root.path("order").path("side").asText()).isEqualTo("SELL");
    }

    private static BuiltOrder makeBuiltOrder(int side) {
        // v2 EIP-712 message — 11 fields exactly.
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("salt", "12345");
        message.put("maker", "0xMAKER");
        message.put("signer", "0xMAKER");
        message.put("tokenId", "999");
        message.put("makerAmount", "1000000");
        message.put("takerAmount", "2000000");
        message.put("side", Integer.toString(side));
        message.put("signatureType", "0");
        message.put("timestamp", Long.toString(System.currentTimeMillis()));
        message.put("metadata", BYTES32_ZERO);
        message.put("builder", BYTES32_ZERO);

        Map<String, Object> typedData = new LinkedHashMap<>();
        typedData.put("message", message);

        // wireOrder = message + API-only `expiration`. ClobOrderClient.submit() reads from here.
        Map<String, Object> wireOrder = new LinkedHashMap<>(message);
        wireOrder.put("expiration", "0");

        return new BuiltOrder("0xhash",
                BigInteger.valueOf(1_000_000),
                BigInteger.valueOf(2_000_000),
                typedData,
                wireOrder);
    }

    /**
     * Returns a string that {@link Base64#getUrlDecoder()} accepts; the L2 signer immediately
     * decodes the secret as URL-safe base64 with padding, so a malformed value would crash before
     * the request fires and we'd never see the captured body.
     */
    private static String base64(String s) {
        return Base64.getUrlEncoder().encodeToString(s.getBytes());
    }
}
