package com.polymarket.tma.trading.bridge;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.polymarket.tma.common.ApiException;
import com.polymarket.tma.config.WebClientConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/** Response bodies are trimmed copies of real bridge.polymarket.com responses (2026-10-11). */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BridgeClientTest {

    private static final String WALLET = "0xa1D17A5546aD9166Cb14071c36a5741d812CbE57";
    private static final String BUILDER_CODE = "0xfd9550a42e3ced40dac8ac02744c8376d44a0f5bf0ca6a6f35c6045284c07122";

    private WireMockServer wm;

    @BeforeAll
    void start() {
        wm = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wm.start();
    }

    @AfterAll
    void stop() {
        wm.stop();
    }

    @BeforeEach
    void reset() {
        wm.resetAll();
    }

    private BridgeClient client(String builderCode) {
        return new BridgeClient(new WebClientConfig().webClientBuilder(),
                new BridgeProperties("http://localhost:" + wm.port(), builderCode));
    }

    @Test
    void depositAddressesSendsWalletAndBuilderCode() {
        wm.stubFor(post("/deposit").willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json")
                .withBody("{\"address\":{\"evm\":\"0x3876b3836e29D395b1e6cC360aBF7dA9501dFd35\","
                        + "\"svm\":\"7MKPDNLbRr8h9geFx9LZERugHoc552SAxMNJFV645Q2s\","
                        + "\"tron\":\"TRhETctHBXPxnMeGTsH1mGWnocPoMMYCvi\","
                        + "\"btc\":\"bc1q6xu2g40j3msgmsw5rhs2qkvyfvuu49t702s8n6\"},"
                        + "\"note\":\"Only certain chains and tokens are supported.\"}")));

        var addresses = client(BUILDER_CODE).depositAddresses(WALLET).block();

        assertThat(addresses.svm()).isEqualTo("7MKPDNLbRr8h9geFx9LZERugHoc552SAxMNJFV645Q2s");
        assertThat(addresses.tron()).isEqualTo("TRhETctHBXPxnMeGTsH1mGWnocPoMMYCvi");
        LoggedRequest req = wm.findAll(postRequestedFor(urlPathEqualTo("/deposit"))).get(0);
        assertThat(req.getBodyAsString()).isEqualTo("{\"address\":\"" + WALLET + "\"}");
        assertThat(req.getHeader("X-Builder-Code")).isEqualTo(BUILDER_CODE);
    }

    @Test
    void malformedBuilderCodeIsNotSent() {
        wm.stubFor(post("/deposit").willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json")
                .withBody("{\"address\":{\"evm\":\"0x1\"}}")));

        client("not-a-bytes32").depositAddresses(WALLET).block();

        assertThat(wm.findAll(postRequestedFor(urlPathEqualTo("/deposit"))).get(0).containsHeader("X-Builder-Code"))
                .isFalse();
    }

    @Test
    void parsesSupportedAssetsAndStatus() {
        wm.stubFor(get("/supported-assets").willReturn(aResponse().withHeader("Content-Type", "application/json")
                .withBody("{\"supportedAssets\":[{\"chainId\":\"728126428\",\"chainName\":\"Tron\",\"token\":"
                        + "{\"name\":\"Tether USD\",\"symbol\":\"USDT\",\"address\":\"TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t\","
                        + "\"decimals\":6},\"minCheckoutUsd\":2}]}")));
        wm.stubFor(get(urlPathEqualTo("/status/7MKP")).withQueryParam("limit", equalTo("20"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("{\"transactions\":[{\"fromChainId\":\"1151111081099710\","
                                + "\"fromTokenAddress\":\"Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB\","
                                + "\"fromAmountBaseUnit\":\"4600000\",\"toChainId\":\"137\","
                                + "\"toTokenAddress\":\"0x2791Bca1f2de4661ED88A30C99A7a9449Aa84174\",\"status\":\"COMPLETED\","
                                + "\"txHash\":\"5QNf\",\"createdTimeMs\":1791670804194}],\"nextCursor\":null}")));

        var assets = client(null).supportedAssets().block();
        var txs = client(null).recentTransactions("7MKP").block();

        assertThat(assets).singleElement().satisfies(a -> {
            assertThat(a.chainName()).isEqualTo("Tron");
            assertThat(a.token().symbol()).isEqualTo("USDT");
            assertThat(a.minCheckoutUsd()).isEqualTo(2.0);
        });
        assertThat(txs).singleElement().satisfies(t -> {
            assertThat(t.status()).isEqualTo("COMPLETED");
            assertThat(t.fromAmountBaseUnit()).isEqualTo("4600000");
            assertThat(t.createdTimeMs()).isEqualTo(1791670804194L);
        });
    }

    @Test
    void withdrawalAddressesAndQuoteSendDestination() throws Exception {
        wm.stubFor(post("/withdraw").willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json")
                .withBody("{\"address\":{\"evm\":\"0x23566f8b2E82aDfCf01846E54899d110e97AC053\"},\"note\":\"Send funds\"}")));
        wm.stubFor(post("/quote").willReturn(aResponse().withHeader("Content-Type", "application/json")
                .withBody("{\"estCheckoutTimeMs\":25000,\"estOutputUsd\":14.48,\"estToTokenBaseUnit\":\"14491203\","
                        + "\"quoteId\":\"0x00c3\",\"estFeeBreakdown\":{\"gasUsd\":0.0038}}")));

        var addresses = client(BUILDER_CODE).withdrawalAddresses(WALLET, "728126428",
                "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t", "TRhETctHBXPxnMeGTsH1mGWnocPoMMYCvi").block();
        var quote = client(null).quote("137", "0xC011a7E12a19f7B1f670d46F03B03f3342E82DFB", "14500000",
                "728126428", "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t", "TRhETctHBXPxnMeGTsH1mGWnocPoMMYCvi").block();

        assertThat(addresses.evm()).isEqualTo("0x23566f8b2E82aDfCf01846E54899d110e97AC053");
        var body = new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                wm.findAll(postRequestedFor(urlPathEqualTo("/withdraw"))).get(0).getBodyAsString());
        assertThat(body.get("address").asText()).isEqualTo(WALLET);
        assertThat(body.get("toChainId").asText()).isEqualTo("728126428");
        assertThat(body.get("recipientAddr").asText()).isEqualTo("TRhETctHBXPxnMeGTsH1mGWnocPoMMYCvi");
        assertThat(wm.findAll(postRequestedFor(urlPathEqualTo("/withdraw"))).get(0).getHeader("X-Builder-Code"))
                .isEqualTo(BUILDER_CODE);
        assertThat(quote.estToTokenBaseUnit()).isEqualTo("14491203");
        assertThat(quote.estCheckoutTimeMs()).isEqualTo(25_000L);
    }

    @Test
    void errorsMapToUpstreamException() {
        wm.stubFor(post("/deposit").willReturn(aResponse().withStatus(400).withBody("{\"error\":\"invalid address\"}")));

        assertThatThrownBy(() -> client(null).depositAddresses("0xbad").block())
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("invalid address");
    }
}
