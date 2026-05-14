package com.polymarket.tma.market.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.polymarket.tma.config.AppProperties;
import com.polymarket.tma.config.WebClientConfig;
import com.polymarket.tma.market.dto.MarketDto;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import reactor.test.StepVerifier;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GammaClientTest {

    private WireMockServer wm;
    private GammaClient client;

    @BeforeAll
    void setUp() {
        wm = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wm.start();
        AppProperties props = new AppProperties(
                new AppProperties.Cors(List.of("*")),
                new AppProperties.Jwt("0123456789012345678901234567890123", Duration.ofMinutes(15), Duration.ofDays(30), "test"),
                new AppProperties.Telegram("test-bot", Duration.ofHours(24)),
                new AppProperties.Polymarket(
                        "http://localhost:" + wm.port(),
                        "http://localhost:" + wm.port(),
                        "http://localhost:" + wm.port(),
                        "ws://localhost:" + wm.port(),
                        Duration.ofSeconds(30), Duration.ofSeconds(5),
                        Duration.ofSeconds(3), Duration.ofMinutes(2)),
                new AppProperties.Polygon("", "", ""),
                new AppProperties.Privy("", ""));
        client = new GammaClient(new WebClientConfig().webClientBuilder(), props);
    }

    @AfterAll
    void tearDown() {
        wm.stop();
    }

    @Test
    void listMarketsParses() {
        // Polymarket Gamma returns array-like fields as JSON-encoded strings.
        wm.stubFor(get(urlPathEqualTo("/markets")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        [{
                          "conditionId": "0xabc",
                          "question": "Will it rain tomorrow?",
                          "slug": "rain",
                          "volume": 12345.67,
                          "active": true,
                          "closed": false,
                          "outcomes": "[\\"Yes\\", \\"No\\"]",
                          "outcomePrices": "[\\"0.6\\", \\"0.4\\"]",
                          "clobTokenIds": "[\\"t1\\",\\"t2\\"]"
                        }]
                        """)));

        StepVerifier.create(client.listMarkets(20, 0, "volume24hr", false, null, null))
                .assertNext(items -> {
                    assertThat(items).hasSize(1);
                    MarketDto m = items.get(0);
                    assertThat(m.conditionId()).isEqualTo("0xabc");
                    assertThat(m.active()).isTrue();
                    assertThat(m.outcomes()).containsExactly("Yes", "No");
                    assertThat(m.outcomePrices()).containsExactly("0.6", "0.4");
                    assertThat(m.clobTokenIds()).containsExactly("t1", "t2");
                })
                .verifyComplete();
    }

    @Test
    void listMarketsUsesPublicSearchWhenSearchSet() {
        wm.stubFor(get(urlPathEqualTo("/public-search")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {
                          "events": [
                            {
                              "markets": [
                                {
                                  "conditionId": "0xsearch1",
                                  "question": "Search hit A?",
                                  "slug": "a",
                                  "volume": "100",
                                  "volume24hr": 50,
                                  "active": true,
                                  "closed": false,
                                  "outcomes": "[\\"Yes\\", \\"No\\"]",
                                  "outcomePrices": "[\\"0.5\\", \\"0.5\\"]",
                                  "clobTokenIds": "[\\"ta\\",\\"tb\\"]"
                                }
                              ]
                            }
                          ],
                          "pagination": { "hasMore": false, "totalResults": 1 }
                        }
                        """)));

        StepVerifier.create(client.listMarkets(20, 0, "volume24hr", false, null, "election"))
                .assertNext(items -> {
                    assertThat(items).hasSize(1);
                    assertThat(items.get(0).conditionId()).isEqualTo("0xsearch1");
                    assertThat(items.get(0).question()).isEqualTo("Search hit A?");
                })
                .verifyComplete();
    }
}
