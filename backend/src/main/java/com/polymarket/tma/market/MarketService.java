package com.polymarket.tma.market;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polymarket.tma.common.ApiException;
import com.polymarket.tma.config.AppProperties;
import com.polymarket.tma.market.client.ClobClient;
import com.polymarket.tma.market.client.GammaClient;
import com.polymarket.tma.market.dto.CommentDto;
import com.polymarket.tma.market.dto.EventDto;
import com.polymarket.tma.market.dto.SeriesDto;
import com.polymarket.tma.market.dto.MarketDto;
import com.polymarket.tma.market.dto.MarketListResponse;
import com.polymarket.tma.market.dto.OrderbookDto;
import com.polymarket.tma.market.dto.PriceHistoryDto;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class MarketService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MarketService.class);
    private static final Pattern CONDITION_ID_HEX = Pattern.compile("^0x[a-fA-F0-9]{64}$");

    private final GammaClient gamma;
    private final ClobClient clob;
    private final MarketCacheService cache;
    private final AppProperties props;
    private final ObjectMapper objectMapper;

    public MarketService(GammaClient gamma,
                         ClobClient clob,
                         MarketCacheService cache,
                         AppProperties props,
                         ObjectMapper objectMapper) {
        this.gamma = gamma;
        this.clob = clob;
        this.cache = cache;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    public Mono<MarketListResponse> list(int page, int size, String order, boolean ascending, String tag, String search) {
        int safeSize = Math.max(1, Math.min(size, 50));
        int safePage = Math.max(0, page);
        int offset = safePage * safeSize;
        String key = "pm:markets:list:" + safeSize + ":" + offset + ":" + order + ":" + ascending + ":" + tag + ":" + search;

        Mono<List<MarketDto>> loader = gamma.listMarkets(safeSize + 1, offset, order, ascending, tag, search);

        return cache.readThrough(key, List.class, props.polymarket().listCacheTtl(), loader.cast(List.class))
                .map(raw -> {
                    List<MarketDto> markets = toMarketDtoList(raw);
                    boolean hasMore = markets.size() > safeSize;
                    List<MarketDto> items = hasMore
                            ? new ArrayList<>(markets.subList(0, safeSize))
                            : new ArrayList<>(markets);
                    return new MarketListResponse(items, safePage, safeSize, hasMore);
                });
    }

    /** Redis JSON cache deserializes records as {@link java.util.LinkedHashMap}; normalize before HTTP JSON. */
    private List<MarketDto> toMarketDtoList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<MarketDto> out = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof MarketDto dto) {
                out.add(dto);
            } else {
                out.add(objectMapper.convertValue(item, MarketDto.class));
            }
        }
        return out;
    }

    public Mono<MarketDto> get(String marketKey) {
        String key = "pm:market:" + marketKey;
        Mono<MarketDto> loader = isHexConditionId(marketKey)
                ? resolveGammaByConditionId(marketKey)
                : gamma.getMarket(marketKey);
        return cache.readThrough(key, MarketDto.class, props.polymarket().detailCacheTtl(), loader)
                .map(this::toMarketDto)
                .switchIfEmpty(Mono.error(ApiException.notFound("MARKET_NOT_FOUND", "Market not found: " + marketKey)));
    }

    private MarketDto toMarketDto(Object raw) {
        if (raw instanceof MarketDto dto) {
            return dto;
        }
        return objectMapper.convertValue(raw, MarketDto.class);
    }

    private Mono<MarketDto> resolveGammaByConditionId(String conditionId) {
        return clob.getMarketSlugByConditionId(conditionId)
                .flatMap(slug -> gamma.listMarketsBySlug(1, 0, "volume24hr", false, slug))
                .flatMap(list -> list.stream()
                        .filter(m -> conditionId.equalsIgnoreCase(m.conditionId()))
                        .findFirst()
                        .map(Mono::just)
                        .orElseGet(() ->
                                Mono.error(ApiException.notFound("MARKET_NOT_FOUND", "Market not found: " + conditionId))));
    }

    private static boolean isHexConditionId(String marketKey) {
        return marketKey != null && CONDITION_ID_HEX.matcher(marketKey).matches();
    }

    public Mono<OrderbookDto> orderbook(String tokenId) {
        String key = "pm:book:" + tokenId;
        return cache.readThrough(key, OrderbookDto.class, props.polymarket().orderbookCacheTtl(), clob.getOrderbook(tokenId));
    }

    /** Chart intervals and their CLOB bucket size in minutes (~60–200 points each). */
    private static final Map<String, Integer> HISTORY_FIDELITY = Map.of(
            "1h", 1, "6h", 5, "1d", 15, "1w", 60, "1m", 180, "max", 180);

    public Mono<PriceHistoryDto> history(String tokenId, String interval) {
        String safeInterval = HISTORY_FIDELITY.containsKey(interval) ? interval : "1d";
        String key = "pm:history:" + tokenId + ":" + safeInterval;
        return cache.readThrough(key, PriceHistoryDto.class, props.polymarket().historyCacheTtl(),
                clob.getPriceHistory(tokenId, safeInterval, HISTORY_FIDELITY.get(safeInterval)));
    }

    /** Polymarket comments are on the parent {@code Event}; resolved via Gamma using the market's condition id. */
    public Mono<List<CommentDto>> comments(String marketKey, int limit, int offset) {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        int safeOffset = Math.max(0, offset);
        return resolveConditionId(marketKey)
                .flatMap(gamma::resolveEventIdForCondition)
                .flatMap(eid -> {
                    try {
                        return gamma.listEventComments(Integer.parseInt(eid), safeLimit, safeOffset, false);
                    } catch (NumberFormatException e) {
                        return Mono.just(Collections.<CommentDto>emptyList());
                    }
                })
                .switchIfEmpty(Mono.just(Collections.emptyList()));
    }

    /** Past events of a series shown under "Past"; older ones are rarely useful on a phone. */
    private static final int SERIES_PAST_LIMIT = 6;

    /**
     * The event {@code marketKey} belongs to, in the user's {@code locale}: its open markets (date
     * ladders by end date, candidates by "Yes" price, as polymarket.com does), its resolved markets,
     * and the other events of its series with the market to open for each.
     */
    public Mono<EventDto> event(String marketKey, String locale) {
        return resolveConditionId(marketKey)
                .flatMap(gamma::resolveEventIdForCondition)
                .flatMap(eventId -> rawEvent(eventId, locale))
                .map(MarketService::openMarketsInOrder)
                .flatMap(event -> seriesEvents(event, locale)
                        .map(series -> new EventDto(event.id(), event.slug(), event.title(), event.markets(),
                                event.resolved(), null, series)))
                .switchIfEmpty(Mono.error(ApiException.notFound("EVENT_NOT_FOUND", "No event for " + marketKey)));
    }

    private Mono<EventDto> rawEvent(String eventId, String locale) {
        return cache.readThrough(
                        "pm:event:" + eventId + ":" + (locale == null ? "en" : locale), EventDto.class,
                        props.polymarket().listCacheTtl(), gamma.getEvent(eventId, locale))
                .map(raw -> raw instanceof EventDto e ? e : objectMapper.convertValue(raw, EventDto.class));
    }

    private Mono<List<EventDto.SeriesEvent>> seriesEvents(EventDto event, String locale) {
        if (event.series() == null || event.series().isEmpty() || event.series().get(0).id() == null) {
            return Mono.just(List.of());
        }
        String seriesId = event.series().get(0).id();
        return cache.readThrough("pm:series:" + seriesId, SeriesDto.class, props.polymarket().listCacheTtl(),
                        gamma.getSeries(seriesId))
                .map(raw -> raw instanceof SeriesDto sd ? sd : objectMapper.convertValue(raw, SeriesDto.class))
                .flatMap(series -> {
                    List<SeriesDto.Event> picked = pickSeriesEvents(series.events(), event.id());
                    if (picked.size() < 2) {
                        return Mono.just(List.<EventDto.SeriesEvent>of());
                    }
                    return Flux.fromIterable(picked)
                            .flatMapSequential(e -> e.id().equals(event.id())
                                    ? Mono.just(new EventDto.SeriesEvent(e.id(), event.title(), e.endDate(),
                                            Boolean.TRUE.equals(e.closed()), true, null))
                                    : rawEvent(e.id(), locale)
                                            .map(MarketService::openMarketsInOrder)
                                            .map(ev -> new EventDto.SeriesEvent(e.id(), ev.title(), e.endDate(),
                                                    Boolean.TRUE.equals(e.closed()), false, entryMarketKey(ev)))
                                            .onErrorResume(err -> Mono.empty()), 4)
                            .filter(se -> se.current() || se.marketKey() != null)
                            .collectList();
                })
                .onErrorResume(e -> {
                    log.warn("Series {} lookup failed: {}", seriesId, e.toString());
                    return Mono.just(List.of());
                });
    }

    /** Open events by end date, then the latest few closed ones; always keeps the current event. */
    static List<SeriesDto.Event> pickSeriesEvents(List<SeriesDto.Event> events, String currentId) {
        if (events == null) {
            return List.of();
        }
        java.util.Comparator<SeriesDto.Event> byEnd = java.util.Comparator.comparing(SeriesDto.Event::endDate,
                java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()));
        List<SeriesDto.Event> valid = events.stream().filter(e -> e.id() != null).toList();
        List<SeriesDto.Event> open = valid.stream()
                .filter(e -> !Boolean.TRUE.equals(e.closed()) || e.id().equals(currentId))
                .sorted(byEnd).toList();
        List<SeriesDto.Event> past = valid.stream()
                .filter(e -> Boolean.TRUE.equals(e.closed()) && !e.id().equals(currentId))
                .sorted(byEnd.reversed()).limit(SERIES_PAST_LIMIT).toList();
        List<SeriesDto.Event> out = new ArrayList<>(past);
        java.util.Collections.reverse(out);
        out.addAll(open);
        return out;
    }

    /** The market to open for a series event: its first open market, else its latest resolved one. */
    private static String entryMarketKey(EventDto ev) {
        MarketDto m = !ev.markets().isEmpty() ? ev.markets().get(0)
                : ev.resolved() != null && !ev.resolved().isEmpty() ? ev.resolved().get(0) : null;
        if (m == null) {
            return null;
        }
        return m.id() != null ? m.id() : m.conditionId();
    }

    static EventDto openMarketsInOrder(EventDto event) {
        List<MarketDto> all = (event.markets() == null ? List.<MarketDto>of() : event.markets()).stream()
                .filter(m -> m.conditionId() != null)
                .toList();
        List<MarketDto> open = all.stream().filter(m -> !Boolean.TRUE.equals(m.closed())).toList();
        boolean dateLadder = open.stream().map(MarketDto::endDate).filter(java.util.Objects::nonNull)
                .distinct().count() > 1;
        java.util.Comparator<MarketDto> order = dateLadder
                ? java.util.Comparator.comparing(MarketDto::endDate,
                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                : java.util.Comparator.comparing(MarketService::yesPrice).reversed();
        List<MarketDto> resolved = all.stream().filter(m -> Boolean.TRUE.equals(m.closed()))
                .sorted(java.util.Comparator.comparing(MarketDto::endDate,
                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())).reversed())
                .toList();
        return new EventDto(event.id(), event.slug(), event.title(), open.stream().sorted(order).toList(),
                resolved, event.series(), null);
    }

    private static double yesPrice(MarketDto m) {
        try {
            return m.outcomePrices() == null || m.outcomePrices().isEmpty()
                    ? 0 : Double.parseDouble(m.outcomePrices().get(0));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private Mono<String> resolveConditionId(String marketKey) {
        if (isHexConditionId(marketKey)) {
            return Mono.just(marketKey);
        }
        return get(marketKey).map(MarketDto::conditionId);
    }
}
