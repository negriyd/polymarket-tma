package com.polymarket.tma.market.i18n;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.polymarket.tma.market.client.GammaClient;
import com.polymarket.tma.market.dto.MarketDto;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class MarketTranslationServiceTest {

    @Mock GammaClient gamma;
    @Mock RedisTemplate<String, Object> redis;
    @Mock ValueOperations<String, Object> values;
    MarketTranslationService service;

    @BeforeEach
    void setUp() {
        service = new MarketTranslationService(gamma, redis);
    }

    private static MarketDto market(String cid, String question) {
        return new MarketDto("1", cid, question, null, "English description", null, null, null, null, null, null,
                true, false, true, false, null, null, null, List.of("Yes", "No"), List.of("1", "2"),
                List.of("0.5", "0.5"), false);
    }

    @Test
    void englishLeavesMarketsUntouched() {
        var markets = List.of(market("0xA", "Will X happen?"));
        assertThat(service.translateMarkets(markets, null).block()).isSameAs(markets);
        verifyNoInteractions(gamma, redis);
    }

    @Test
    void overlaysCachedAndFetchedTitlesAndCachesTheNewOnes() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.multiGet(List.of("pm:title:ru:0xa", "pm:title:ru:0xb", "pm:title:ru:0xc")))
                .thenReturn(Arrays.asList("Случится ли A?", null, null));
        // Gamma returns only 0xB; 0xC has no data and stays English.
        when(gamma.marketsByConditionIds(List.of("0xb", "0xc"), "ru"))
                .thenReturn(Mono.just(List.of(market("0xB", "Случится ли B?"))));
        // 0xC isn't open: looked up again among closed markets, where it isn't either.
        when(gamma.marketsByConditionIds(List.of("0xc"), "ru", true)).thenReturn(Mono.just(List.of()));

        var out = service.translateMarkets(List.of(
                market("0xA", "Will A happen?"), market("0xB", "Will B happen?"), market("0xC", "Will C happen?")),
                "ru").block();

        assertThat(out).extracting(MarketDto::question)
                .containsExactly("Случится ли A?", "Случится ли B?", "Will C happen?");
        assertThat(out.get(0).description()).isEqualTo("English description");
        verify(values).set("pm:title:ru:0xb", "Случится ли B?", Duration.ofHours(6));
    }

    @Test
    void gammaFailureFallsBackToEnglish() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.multiGet(anyList())).thenReturn(Arrays.asList((Object) null));
        when(gamma.marketsByConditionIds(anyList(), eq("es"))).thenReturn(Mono.error(new RuntimeException("down")));

        var markets = List.of(market("0xA", "Will A happen?"));
        assertThat(service.translateMarkets(markets, "es").block()).isEqualTo(markets);
        verify(values, never()).set(any(), any(), any(Duration.class));
    }

    @Test
    void readsFirstSupportedLanguageFromHeader() {
        assertThat(MarketLocale.fromHeader("ru-RU,ru;q=0.9,en;q=0.8")).isEqualTo("ru");
        assertThat(MarketLocale.fromHeader("pt-BR")).isEqualTo("pt");
        assertThat(MarketLocale.fromHeader("zh")).isEqualTo("zh");
        assertThat(MarketLocale.fromHeader("en-US")).isNull();
        assertThat(MarketLocale.fromHeader("xx")).isNull();
        assertThat(MarketLocale.fromHeader(null)).isNull();
    }
}
