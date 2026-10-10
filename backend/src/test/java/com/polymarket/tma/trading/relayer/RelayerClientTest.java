package com.polymarket.tma.trading.relayer;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.polymarket.tma.common.ApiException;
import com.polymarket.tma.config.WebClientConfig;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/** Wire shape of relayer requests, mirroring {@code @polymarket/builder-relayer-client} 0.0.10. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RelayerClientTest {

    private static final String FACTORY = "0x00000000000Fb5C9ADea0298D729A0CB3823Cc07";
    private static final String BEACON = "0x7A18EDfe055488A3128f01F563e5B479D92ffc3a";
    private static final String OWNER = "0x415aE735eB964da56C11D0a53F0f082b85FcbEA5";
    private static final String WALLET = "0xa1D17A5546aD9166Cb14071c36a5741d812CbE57";
    private static final String SECRET = "dGVzdC1zZWNyZXQtZm9yLXZlY3RvcnMtb25seS0xMjM0NTY3OA==";

    private WireMockServer wm;
    private RelayerClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeAll
    void setUp() {
        wm = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wm.start();
        client = new RelayerClient(new WebClientConfig().webClientBuilder(), props(SECRET), mapper);
    }

    @AfterAll
    void tearDown() {
        wm.stop();
    }

    @BeforeEach
    void reset() {
        wm.resetAll();
    }

    private RelayerProperties props(String secret) {
        return new RelayerProperties("http://localhost:" + wm.port(), "builder-key", secret, "builder-pass",
                FACTORY, BEACON, "0x58CA52ebe0DadfdF531Cde7062e76746de4Db1eB", null, null, null, null, null, null);
    }

    @Test
    void depositWalletAddressUsesBeaconDerivation() {
        assertThat(client.depositWalletAddress(OWNER)).isEqualTo(WALLET);
    }

    @Test
    void createWalletPostsWalletCreateWithBuilderHeaders() throws Exception {
        wm.stubFor(post("/submit").willReturn(aResponse().withHeader("Content-Type", "application/json")
                .withBody("{\"transactionID\":\"tx-1\",\"state\":\"STATE_NEW\"}")));

        var tx = client.createWallet(OWNER).block();

        assertThat(tx.transactionID()).isEqualTo("tx-1");
        LoggedRequest req = wm.findAll(postRequestedFor(urlPathEqualTo("/submit"))).get(0);
        assertThat(req.getBodyAsString()).isEqualTo(
                "{\"type\":\"WALLET-CREATE\",\"from\":\"" + OWNER + "\",\"to\":\"" + FACTORY + "\"}");
        assertThat(req.getHeader("POLY_BUILDER_API_KEY")).isEqualTo("builder-key");
        assertThat(req.getHeader("POLY_BUILDER_PASSPHRASE")).isEqualTo("builder-pass");
        long ts = Long.parseLong(req.getHeader("POLY_BUILDER_TIMESTAMP"));
        assertThat(req.getHeader("POLY_BUILDER_SIGNATURE"))
                .as("HMAC covers exactly the body sent on the wire")
                .isEqualTo(BuilderAuthSigner.signature(SECRET, ts, "POST", "/submit", req.getBodyAsString()));
    }

    @Test
    void submitBatchSendsDepositWalletParams() throws Exception {
        wm.stubFor(post("/submit").willReturn(aResponse().withHeader("Content-Type", "application/json")
                .withBody("{\"transactionID\":\"tx-2\",\"state\":\"STATE_NEW\"}")));
        var call = new DepositWalletBatch.Call("0xC011a7E12a19f7B1f670d46F03B03f3342E82DFB", "0", "0x095ea7b3");

        client.submitBatch(OWNER, WALLET, "3", "1791671000", List.of(call), "0xsig").block();

        JsonNode body = mapper.readTree(wm.findAll(postRequestedFor(urlPathEqualTo("/submit"))).get(0).getBodyAsString());
        assertThat(body.get("type").asText()).isEqualTo("WALLET");
        assertThat(body.get("from").asText()).isEqualTo(OWNER);
        assertThat(body.get("to").asText()).isEqualTo(FACTORY);
        assertThat(body.get("nonce").asText()).isEqualTo("3");
        assertThat(body.get("signature").asText()).isEqualTo("0xsig");
        JsonNode params = body.get("depositWalletParams");
        assertThat(params.get("depositWallet").asText()).isEqualTo(WALLET);
        assertThat(params.get("deadline").asText()).isEqualTo("1791671000");
        assertThat(params.get("calls").get(0).get("target").asText()).isEqualTo(call.target());
        assertThat(params.get("calls").get(0).get("value").asText()).isEqualTo("0");
        assertThat(params.get("calls").get(0).get("data").asText()).isEqualTo("0x095ea7b3");
    }

    @Test
    void readEndpointsParseResponses() {
        wm.stubFor(get(urlPathEqualTo("/nonce")).withQueryParam("address", equalTo(OWNER))
                .withQueryParam("type", equalTo("WALLET"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("{\"nonce\":\"5\"}")));
        wm.stubFor(get(urlPathEqualTo("/deployed")).withQueryParam("address", equalTo(WALLET))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("{\"deployed\":true}")));
        wm.stubFor(get(urlPathEqualTo("/transaction")).withQueryParam("id", equalTo("tx-1"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(
                        "[{\"transactionID\":\"tx-1\",\"state\":\"STATE_CONFIRMED\",\"transactionHash\":\"0xabc\","
                                + "\"proxyAddress\":\"" + WALLET + "\",\"extra\":1}]")));

        assertThat(client.nonce(OWNER).block()).isEqualTo("5");
        assertThat(client.isDeployed(WALLET).block()).isTrue();
        var tx = client.getTransaction("tx-1").block();
        assertThat(tx.isSuccess()).isTrue();
        assertThat(tx.proxyAddress()).isEqualTo(WALLET);
    }

    @Test
    void submitErrorMapsToUpstreamException() {
        wm.stubFor(post("/submit").willReturn(aResponse().withStatus(401).withBody("{\"error\":\"invalid builder auth\"}")));

        assertThatThrownBy(() -> client.createWallet(OWNER).block())
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("invalid builder auth");
    }

    @Test
    void submitWithoutBuilderCredentialsFailsFast() {
        var unconfigured = new RelayerClient(new WebClientConfig().webClientBuilder(), props(""), mapper);

        assertThatThrownBy(() -> unconfigured.createWallet(OWNER).block())
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not configured");
        assertThat(wm.getAllServeEvents()).isEmpty();
    }
}
