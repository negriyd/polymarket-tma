package com.polymarket.tma.market.i18n;

import com.polymarket.tma.market.client.GammaClient;
import com.polymarket.tma.market.dto.MarketDto;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Translated market titles, as polymarket.com shows them. Gamma serves them for {@code ?locale=xx}
 * (question only — descriptions stay English there too), but its search endpoint ignores the locale,
 * so titles are overlaid by condition id after the English data is loaded. Each title is cached per
 * locale; a translation failure falls back to English instead of failing the page.
 */
@Service
public class MarketTranslationService {

    private static final Logger log = LoggerFactory.getLogger(MarketTranslationService.class);
    private static final Duration TTL = Duration.ofHours(6);
    private static final Duration LOOKUP_TIMEOUT = Duration.ofSeconds(5);
    /** Gamma accepts repeated condition_ids; keep URLs well under common length limits. */
    private static final int BATCH = 40;

    private final GammaClient gamma;
    private final RedisTemplate<String, Object> redis;

    public MarketTranslationService(GammaClient gamma, RedisTemplate<String, Object> redis) {
        this.gamma = gamma;
        this.redis = redis;
    }

    public Mono<List<MarketDto>> translateMarkets(List<MarketDto> markets, String locale) {
        return translate(markets, locale, MarketDto::conditionId, MarketDto::withQuestion);
    }

    public Mono<MarketDto> translateMarket(MarketDto market, String locale) {
        return translateMarkets(List.of(market), locale).map(list -> list.get(0));
    }

    /** Replaces each item's title with its translation where one exists; other items are unchanged. */
    public <T> Mono<List<T>> translate(List<T> items, String locale,
                                       Function<T, String> conditionId, BiFunction<T, String, T> withTitle) {
        if (locale == null || items.isEmpty()) {
            return Mono.just(items);
        }
        List<String> ids = items.stream().map(conditionId).filter(id -> id != null && !id.isBlank())
                .map(id -> id.toLowerCase(Locale.ROOT)).distinct().toList();
        return titles(ids, locale)
                .map(titles -> items.stream().map(item -> {
                    String id = conditionId.apply(item);
                    String title = id == null ? null : titles.get(id.toLowerCase(Locale.ROOT));
                    return title == null || title.isBlank() ? item : withTitle.apply(item, title);
                }).toList())
                .onErrorResume(e -> {
                    log.warn("Market title translation ({}) failed, serving English: {}", locale, e.toString());
                    return Mono.just(items);
                });
    }

    private Mono<Map<String, String>> titles(List<String> conditionIds, String locale) {
        Map<String, String> found = new HashMap<>();
        List<String> missing = new ArrayList<>();
        List<String> keys = conditionIds.stream().map(id -> key(locale, id)).toList();
        List<Object> cached = cachedValues(keys);
        for (int i = 0; i < conditionIds.size(); i++) {
            Object v = cached == null ? null : cached.get(i);
            if (v instanceof String s) {
                found.put(conditionIds.get(i), s);
            } else {
                missing.add(conditionIds.get(i));
            }
        }
        if (missing.isEmpty()) {
            return Mono.just(found);
        }
        // Open markets first; whatever is still missing is looked up among closed ones.
        return Flux.fromIterable(partition(missing))
                .flatMap(batch -> gamma.marketsByConditionIds(batch, locale)
                        .flatMap(open -> {
                            java.util.Set<String> seen = new java.util.HashSet<>();
                            open.forEach(m -> seen.add(m.conditionId() == null ? "" : m.conditionId().toLowerCase(Locale.ROOT)));
                            List<String> rest = batch.stream().filter(id -> !seen.contains(id)).toList();
                            return rest.isEmpty() ? Mono.just(open)
                                    : gamma.marketsByConditionIds(rest, locale, true).map(closed -> {
                                        List<MarketDto> all = new ArrayList<>(open);
                                        all.addAll(closed);
                                        return all;
                                    });
                        }))
                .flatMapIterable(list -> list)
                .collectList()
                .timeout(LOOKUP_TIMEOUT)
                .map(fetched -> {
                    for (MarketDto m : fetched) {
                        if (m.conditionId() == null || m.question() == null) {
                            continue;
                        }
                        String id = m.conditionId().toLowerCase(Locale.ROOT);
                        found.put(id, m.question());
                        store(key(locale, id), m.question());
                    }
                    return found;
                });
    }

    private List<Object> cachedValues(List<String> keys) {
        try {
            return redis.opsForValue().multiGet(keys);
        } catch (RuntimeException e) {
            log.warn("Redis multiGet failed for market titles: {}", e.toString());
            return null;
        }
    }

    private void store(String key, String title) {
        try {
            redis.opsForValue().set(key, title, TTL);
        } catch (RuntimeException e) {
            log.warn("Redis set failed for {}: {}", key, e.toString());
        }
    }

    private static String key(String locale, String conditionId) {
        return "pm:title:" + locale + ":" + conditionId;
    }

    private static List<List<String>> partition(Collection<String> ids) {
        List<List<String>> out = new ArrayList<>();
        List<String> all = new ArrayList<>(new LinkedHashSet<>(ids));
        for (int i = 0; i < all.size(); i += BATCH) {
            out.add(all.subList(i, Math.min(i + BATCH, all.size())));
        }
        return out;
    }
}
