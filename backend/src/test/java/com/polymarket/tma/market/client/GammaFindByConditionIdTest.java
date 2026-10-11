package com.polymarket.tma.market.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.polymarket.tma.config.AppProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.reactive.function.client.WebClient;

class GammaFindByConditionIdTest {

    private WireMockServer wm;
    private GammaClient gamma;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wm.start();
        AppProperties props = Mockito.mock(AppProperties.class, Mockito.RETURNS_DEEP_STUBS);
        Mockito.when(props.polymarket().gammaBaseUrl()).thenReturn(wm.baseUrl());
        gamma = new GammaClient(WebClient.builder(), props);
    }

    @AfterEach
    void tearDown() {
        wm.stop();
    }

    @Test
    void findsClosedMarketsThatTheDefaultListingHides() {
        wm.stubFor(get(urlPathEqualTo("/markets")).withQueryParam("condition_ids", equalTo("0xc"))
                .withQueryParam("closed", absent())
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("[]")));
        wm.stubFor(get(urlPathEqualTo("/markets")).withQueryParam("condition_ids", equalTo("0xc"))
                .withQueryParam("closed", equalTo("true"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("[{\"id\":\"7\",\"conditionId\":\"0xc\",\"question\":\"Q?\",\"closed\":true}]")));

        var m = gamma.findByConditionId("0xc").block();
        assertThat(m).isNotNull();
        assertThat(m.closed()).isTrue();
        assertThat(m.id()).isEqualTo("7");
    }
}
