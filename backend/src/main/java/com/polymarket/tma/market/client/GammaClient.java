package com.polymarket.tma.market.client;

import com.polymarket.tma.common.ApiException;
import com.polymarket.tma.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.polymarket.tma.market.dto.CommentDto;
import com.polymarket.tma.market.dto.MarketDto;
import com.polymarket.tma.market.dto.PriceHistoryDto;
import com.polymarket.tma.market.dto.gamma.PublicSearchResponse;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

@Component
public class GammaClient {

    private static final Logger log = LoggerFactory.getLogger(GammaClient.class);

    private final WebClient client;
    private final MeterRegistry meters;

    @Autowired
    public GammaClient(WebClient.Builder builder, AppProperties props, MeterRegistry meters) {
        this.client = builder.baseUrl(props.polymarket().gammaBaseUrl()).build();
        this.meters = meters;
    }

    /** Backwards-compatible ctor used by tests that do not bring up a MeterRegistry. */
    public GammaClient(WebClient.Builder builder, AppProperties props) {
        this(builder, props, new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
    }

    private static final int MAX_SEARCH_PAGES = 12;
    private static final int SEARCH_EVENTS_LIMIT = 45;

    public Mono<List<MarketDto>> listMarkets(int limit, int offset, String order, boolean ascending, String tag, String search) {
        if (search != null && !search.isBlank()) {
            return searchMarkets(limit, offset, order, ascending, search);
        }
        return client.get()
                .uri(uri -> buildListUri(uri, limit, offset, order, ascending, tag, null))
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::mapError)
                .bodyToMono(new ParameterizedTypeReference<List<MarketDto>>() {})
                .retryWhen(retrySpec("listMarkets"))
                .doOnError(e -> log.warn("Gamma listMarkets failed: {}", e.toString()));
    }

    /**
     * Text search via Gamma {@code /public-search} (the {@code /markets} list endpoint ignores free-text {@code search}).
     */
    private Mono<List<MarketDto>> searchMarkets(int limit, int offset, String order, boolean ascending, String queryRaw) {
        String q = queryRaw.trim();
        if (q.isEmpty()) {
            return listMarkets(limit, offset, order, ascending, null, null);
        }
        return Mono.fromCallable(() -> searchMarketsBlocking(limit, offset, order, ascending, q))
                .subscribeOn(Schedulers.boundedElastic())
                .retryWhen(retrySpec("publicSearch"))
                .doOnError(e -> log.warn("Gamma publicSearch failed: {}", e.toString()));
    }

    private List<MarketDto> searchMarketsBlocking(int limit, int offset, String order, boolean ascending, String q) {
        int needEnd = offset + limit;
        Map<String, MarketDto> byCond = new LinkedHashMap<>();
        int apiPage = 0;
        boolean hasMore = true;

        while (byCond.size() < needEnd && hasMore && apiPage < MAX_SEARCH_PAGES) {
            final int pageToFetch = apiPage;
            PublicSearchResponse resp = client.get()
                    .uri(uri -> uri.path("/public-search")
                            .queryParam("q", q)
                            .queryParam("page", pageToFetch)
                            .queryParam("limit_per_type", SEARCH_EVENTS_LIMIT)
                            .queryParam("events_status", "active")
                            .queryParam("search_tags", false)
                            .queryParam("search_profiles", false)
                            .build())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, this::mapError)
                    .bodyToMono(PublicSearchResponse.class)
                    .block(Duration.ofSeconds(30));

            if (resp == null) {
                break;
            }

            int before = byCond.size();

            if (resp.events() != null) {
                for (PublicSearchResponse.GammaSearchEvent ev : resp.events()) {
                    if (ev == null || ev.markets() == null) {
                        continue;
                    }
                    for (MarketDto m : ev.markets()) {
                        if (m == null || m.conditionId() == null || m.conditionId().isBlank()) {
                            continue;
                        }
                        byCond.putIfAbsent(m.conditionId(), m);
                    }
                }
            }

            hasMore = resp.pagination() != null
                    && resp.pagination().hasMore() != null
                    && resp.pagination().hasMore();

            if (resp.events() == null || resp.events().isEmpty() || byCond.size() == before) {
                break;
            }
            apiPage++;
        }

        List<MarketDto> sorted = new ArrayList<>(byCond.values());
        sorted.sort(marketComparator(order, ascending));

        if (offset >= sorted.size()) {
            return List.of();
        }
        int to = Math.min(needEnd, sorted.size());
        return new ArrayList<>(sorted.subList(offset, to));
    }

    private static Comparator<MarketDto> marketComparator(String order, boolean ascending) {
        String o = order == null || order.isBlank() ? "volume24hr" : order;
        Comparator<MarketDto> c = switch (o) {
            case "volume" -> Comparator.comparing(MarketDto::volume, Comparator.nullsLast(Comparator.naturalOrder()));
            case "liquidity" -> Comparator.comparing(
                    MarketDto::liquidity, Comparator.nullsLast(Comparator.naturalOrder()));
            case "end_date" -> Comparator.comparing(
                    MarketDto::endDate, Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(MarketDto::volume24h, Comparator.nullsLast(Comparator.naturalOrder()));
        };
        return ascending ? c : c.reversed();
    }

    /**
     * List markets filtered by Gamma {@code slug} (used when resolving a condition id via CLOB metadata).
     */
    public Mono<List<MarketDto>> listMarketsBySlug(int limit, int offset, String order, boolean ascending, String slug) {
        return client.get()
                .uri(uri -> buildListUri(uri, limit, offset, order, ascending, null, slug))
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::mapError)
                .bodyToMono(new ParameterizedTypeReference<List<MarketDto>>() {})
                .retryWhen(retrySpec("listMarketsBySlug"))
                .doOnError(e -> log.warn("Gamma listMarketsBySlug failed: {}", e.toString()));
    }

    public Mono<MarketDto> getMarket(String conditionId) {
        return client.get()
                .uri(uri -> uri.path("/markets/{id}").build(conditionId))
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::mapError)
                .bodyToMono(MarketDto.class)
                .retryWhen(retrySpec("getMarket"))
                .doOnError(e -> log.warn("Gamma getMarket({}) failed: {}", conditionId, e.toString()));
    }

    public Mono<PriceHistoryDto> getPriceHistory(String tokenId, String interval) {
        return client.get()
                .uri(uri -> uri.path("/prices-history")
                        .queryParam("market", tokenId)
                        .queryParam("interval", interval)
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::mapError)
                .bodyToMono(PriceHistoryDto.class)
                .retryWhen(retrySpec("getPriceHistory"))
                .doOnError(e -> log.warn("Gamma getPriceHistory({}) failed: {}", tokenId, e.toString()));
    }

    /**
     * Resolves Polymarket <strong>event</strong> id for comments — Gamma attaches comments to {@code Event},
     * not {@code market}. Uses {@code GET /markets?condition_ids=…} which includes embedded {@code events}.
     */
    public Mono<String> resolveEventIdForCondition(String conditionId) {
        if (conditionId == null || conditionId.isBlank()) {
            return Mono.empty();
        }
        return client.get()
                .uri(uri -> uri.path("/markets")
                        .queryParam("condition_ids", conditionId)
                        .queryParam("limit", 1)
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::mapError)
                .bodyToMono(JsonNode.class)
                .map(GammaClient::firstEventIdFromMarketsResponse)
                .filter(id -> id != null && !id.isBlank())
                .retryWhen(retrySpec("resolveEventId"))
                .doOnError(e -> log.warn("Gamma resolveEventIdForCondition failed: {}", e.toString()));
    }

    /**
     * @param ascending {@code false} = newest first (typical for comment threads).
     */
    public Mono<List<CommentDto>> listEventComments(int eventId, int limit, int offset, boolean ascending) {
        if (eventId <= 0) {
            return Mono.just(Collections.emptyList());
        }
        int safeLimit = Math.max(1, Math.min(limit, 50));
        int safeOffset = Math.max(0, offset);
        return client.get()
                .uri(uri -> uri.path("/comments")
                        .queryParam("limit", safeLimit)
                        .queryParam("offset", safeOffset)
                        .queryParam("parent_entity_type", "Event")
                        .queryParam("parent_entity_id", eventId)
                        .queryParam("ascending", ascending)
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::mapError)
                .bodyToMono(new ParameterizedTypeReference<List<JsonNode>>() {})
                .map(GammaClient::mapComments)
                .defaultIfEmpty(Collections.emptyList())
                .retryWhen(retrySpec("listEventComments"))
                .doOnError(e -> log.warn("Gamma listEventComments failed: {}", e.toString()));
    }

    private static String firstEventIdFromMarketsResponse(JsonNode root) {
        if (root == null || !root.isArray() || root.size() == 0) {
            return null;
        }
        JsonNode events = root.get(0).path("events");
        if (!events.isArray() || events.size() == 0) {
            return null;
        }
        JsonNode idNode = events.get(0).get("id");
        return idNode == null || idNode.isNull() ? null : idNode.asText();
    }

    private static List<CommentDto> mapComments(List<JsonNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return List.of();
        }
        List<CommentDto> out = new ArrayList<>(nodes.size());
        for (JsonNode n : nodes) {
            CommentDto c = mapOneComment(n);
            if (c != null) {
                out.add(c);
            }
        }
        return out;
    }

    private static CommentDto mapOneComment(JsonNode n) {
        if (n == null || n.isNull()) {
            return null;
        }
        String id = textOrNull(n, "id");
        String body = textOrNull(n, "body");
        if (id == null) {
            return null;
        }
        Instant created = parseInstant(textOrNull(n, "createdAt"));
        JsonNode profile = n.path("profile");
        String name = textOrNull(profile, "name");
        String pseudonym = textOrNull(profile, "pseudonym");
        String author = name != null && !name.isBlank() ? name
                : (pseudonym != null && !pseudonym.isBlank() ? pseudonym : "Anonymous");
        String avatar = textOrNull(profile, "profileImage");
        return new CommentDto(id, body != null ? body : "", author, avatar, created);
    }

    private static String textOrNull(JsonNode n, String field) {
        if (n == null || n.isMissingNode() || n.isNull()) {
            return null;
        }
        JsonNode v = n.get(field);
        if (v == null || v.isNull() || v.isMissingNode()) {
            return null;
        }
        String s = v.asText();
        return s.isBlank() ? null : s;
    }

    private static Instant parseInstant(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (Exception e) {
            return null;
        }
    }

    private static java.net.URI buildListUri(UriBuilder uri, int limit, int offset, String order,
                                             boolean ascending, String tag, String slug) {
        uri.path("/markets")
                .queryParam("limit", limit)
                .queryParam("offset", offset)
                .queryParam("active", true)
                .queryParam("closed", false)
                .queryParam("order", order == null ? "volume24hr" : order)
                .queryParam("ascending", ascending);
        if (tag != null && !tag.isBlank()) {
            uri.queryParam("tag_id", tag);
        }
        if (slug != null && !slug.isBlank()) {
            uri.queryParam("slug", slug);
        }
        return uri.build();
    }

    private Mono<? extends Throwable> mapError(org.springframework.web.reactive.function.client.ClientResponse resp) {
        meters.counter("polymarket_upstream_errors_total",
                "upstream", "gamma",
                "status", Integer.toString(resp.statusCode().value()))
                .increment();
        return resp.bodyToMono(String.class).defaultIfEmpty("").map(body ->
                ApiException.upstream("GAMMA_HTTP_" + resp.statusCode().value(),
                        "Gamma API error: " + resp.statusCode().value() + " " + body));
    }

    private Retry retrySpec(String op) {
        return Retry.backoff(2, Duration.ofMillis(200))
                .maxBackoff(Duration.ofSeconds(2))
                .jitter(0.5)
                .filter(this::isRetryable)
                .doBeforeRetry(s -> log.info("Retrying {} attempt {}", op, s.totalRetries() + 1));
    }

    private boolean isRetryable(Throwable t) {
        if (t instanceof ApiException api) {
            return api.getCode().startsWith("GAMMA_HTTP_5") || api.getCode().equals("GAMMA_HTTP_429");
        }
        return true;
    }
}
