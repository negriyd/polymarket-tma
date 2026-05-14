package com.polymarket.tma.ws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polymarket.tma.config.AppProperties;
import com.polymarket.tma.market.MarketService;
import com.polymarket.tma.market.dto.MarketDto;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.util.retry.Retry;

/**
 * Maintains a single upstream WebSocket to Polymarket CLOB and fans out updates to STOMP clients.
 * <p>
 * The CLOB market channel subscribes by <strong>clob token / asset ids</strong> (see Polymarket docs:
 * {@code assets_ids} + {@code type: market} or {@code operation: subscribe / unsubscribe}). STOMP clients
 * still use {@code /topic/market/{conditionId}}; we resolve tokens from Gamma via {@link MarketService}.
 */
@Component
public class MarketStreamPublisher {

    private static final Logger log = LoggerFactory.getLogger(MarketStreamPublisher.class);
    private static final String TOPIC_PREFIX = "/topic/market/";
    private static final Duration MARKET_LOAD_TIMEOUT = Duration.ofSeconds(20);

    private final AppProperties props;
    private final MarketService marketService;
    private final SimpMessagingTemplate broker;
    private final ObjectMapper mapper;

    /** Canonical (lowercase) condition id -> refcount */
    private final Map<String, Integer> subscriberCount = new ConcurrentHashMap<>();
    /** sessionId+subId -> canonical condition id */
    private final Map<String, String> sessionToTopic = new ConcurrentHashMap<>();
    /** Canonical condition id -> CLOB asset ids for unsubscribe */
    private final Map<String, List<String>> conditionAssets = new ConcurrentHashMap<>();
    /** CLOB asset id -> canonical condition id (routing when payload only has asset_id) */
    private final Map<String, String> assetToCondition = new ConcurrentHashMap<>();

    private final Sinks.Many<String> outbound = Sinks.many().multicast().onBackpressureBuffer(256, false);

    private final AtomicReference<Disposable> upstream = new AtomicReference<>();

    /** First payload on each new upstream TCP session must use {@code type: market} (per Polymarket WS API). */
    private volatile boolean initialUpstreamPayloadSentThisSession;

    public MarketStreamPublisher(
            AppProperties props, MarketService marketService, SimpMessagingTemplate broker, ObjectMapper mapper) {
        this.props = props;
        this.marketService = marketService;
        this.broker = broker;
        this.mapper = mapper;
    }

    private synchronized void ensureUpstream() {
        if (upstream.get() != null) {
            return;
        }
        ReactorNettyWebSocketClient client = new ReactorNettyWebSocketClient();
        URI uri = URI.create(props.polymarket().wsUrl());

        Disposable d = client.execute(uri, session -> {
                    initialUpstreamPayloadSentThisSession = false;
                    Mono<Void> sendLoop = session.send(outbound.asFlux()
                            .map(session::textMessage));
                    Mono<Void> recvLoop = session.receive()
                            .map(WebSocketMessage::getPayloadAsText)
                            .doOnNext(this::handleUpstreamMessage)
                            .then();
                    return Mono.zip(sendLoop, recvLoop).then();
                })
                .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(2))
                        .maxBackoff(Duration.ofMinutes(1))
                        .jitter(0.5)
                        .doBeforeRetry(rs -> log.warn("Reconnecting CLOB WS, attempt {}", rs.totalRetries() + 1)))
                .doOnError(e -> log.error("Upstream WS terminated", e))
                .subscribe();
        upstream.set(d);
        log.info("CLOB upstream WebSocket initialised at {}", uri);
    }

    private void handleUpstreamMessage(String payload) {
        try {
            JsonNode node = mapper.readTree(payload);
            String marketId = firstNonBlank(
                    node.path("market").asText(null),
                    node.path("condition_id").asText(null));
            if (marketId == null || marketId.isBlank()) {
                String assetId = node.path("asset_id").asText(null);
                if (assetId != null && !assetId.isBlank()) {
                    marketId = assetToCondition.get(assetId);
                }
            }
            if (marketId == null || marketId.isBlank()) {
                return;
            }
            broker.convertAndSend(TOPIC_PREFIX + canonicalConditionId(marketId), payload);
        } catch (JsonProcessingException ex) {
            log.debug("Upstream payload not JSON: {}", payload);
        }
    }

    @EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
        String destination = (String) event.getMessage().getHeaders().get("simpDestination");
        String sessionId = (String) event.getMessage().getHeaders().get("simpSessionId");
        String subId = (String) event.getMessage().getHeaders().get("simpSubscriptionId");
        if (destination == null || !destination.startsWith(TOPIC_PREFIX)) {
            return;
        }
        String rawConditionId = destination.substring(TOPIC_PREFIX.length());
        String conditionId = canonicalConditionId(rawConditionId);

        sessionToTopic.put(sessionKey(sessionId, subId), conditionId);
        int next = subscriberCount.merge(conditionId, 1, Integer::sum);
        if (next != 1) {
            return;
        }

        List<String> assets;
        try {
            assets = loadAssetIds(conditionId);
        } catch (Exception ex) {
            log.warn("Cannot resolve CLOB assets for stream (condition={}): {}", conditionId, ex.toString());
            rollbackSubscribe(sessionId, subId, conditionId);
            return;
        }
        if (assets.isEmpty()) {
            log.warn("No clobTokenIds for market {}; live stream disabled for this topic", conditionId);
            rollbackSubscribe(sessionId, subId, conditionId);
            return;
        }

        registerAssets(conditionId, assets);
        ensureUpstream();
        sendUpstreamSubscribe(assets);
        log.debug("Upstream subscribe assets for condition={} ({} ids)", conditionId, assets.size());
    }

    @EventListener
    public void onUnsubscribe(SessionUnsubscribeEvent event) {
        String sessionId = (String) event.getMessage().getHeaders().get("simpSessionId");
        String subId = (String) event.getMessage().getHeaders().get("simpSubscriptionId");
        String conditionId = sessionToTopic.remove(sessionKey(sessionId, subId));
        if (conditionId == null) {
            return;
        }
        Integer remaining = subscriberCount.computeIfPresent(conditionId, (k, v) -> v <= 1 ? null : v - 1);
        if (remaining == null) {
            List<String> assets = unregisterAssets(conditionId);
            if (!assets.isEmpty()) {
                sendUpstreamUnsubscribe(assets);
            }
            log.debug("Upstream unsubscribe assets for condition={} (refcount=0)", conditionId);
        }
    }

    private List<String> loadAssetIds(String conditionId) {
        MarketDto m = marketService.get(conditionId).block(MARKET_LOAD_TIMEOUT);
        if (m == null) {
            return List.of();
        }
        return extractAssetIds(m);
    }

    private static List<String> extractAssetIds(MarketDto m) {
        if (m.clobTokenIds() == null || m.clobTokenIds().isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String id : m.clobTokenIds()) {
            if (id != null && !id.isBlank()) {
                out.add(id.trim());
            }
        }
        return out;
    }

    private void registerAssets(String conditionId, List<String> assets) {
        conditionAssets.put(conditionId, List.copyOf(assets));
        for (String a : assets) {
            assetToCondition.put(a, conditionId);
        }
    }

    private List<String> unregisterAssets(String conditionId) {
        List<String> assets = conditionAssets.remove(conditionId);
        if (assets == null) {
            return List.of();
        }
        for (String a : assets) {
            assetToCondition.remove(a, conditionId);
        }
        return assets;
    }

    private void rollbackSubscribe(String sessionId, String subId, String conditionId) {
        sessionToTopic.remove(sessionKey(sessionId, subId));
        subscriberCount.remove(conditionId);
    }

    private void sendUpstreamSubscribe(List<String> assets) {
        synchronized (this) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("assets_ids", assets);
            body.put("custom_feature_enabled", true);
            if (!initialUpstreamPayloadSentThisSession) {
                body.put("type", "market");
                initialUpstreamPayloadSentThisSession = true;
            } else {
                body.put("operation", "subscribe");
            }
            sendJson(body);
        }
    }

    private void sendUpstreamUnsubscribe(List<String> assets) {
        if (assets.isEmpty()) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("assets_ids", assets);
        body.put("operation", "unsubscribe");
        sendJson(body);
    }

    private void sendJson(Map<String, Object> body) {
        try {
            send(mapper.writeValueAsString(body));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private void send(String payload) {
        Sinks.EmitResult result = outbound.tryEmitNext(payload);
        if (result.isFailure()) {
            log.warn("Failed to enqueue upstream payload: {}", result);
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }

    private static String canonicalConditionId(String id) {
        if (id == null) {
            return "";
        }
        return id.toLowerCase();
    }

    private static String sessionKey(String sessionId, String subId) {
        return sessionId + ":" + subId;
    }

    /** Test/diagnostic helpers. */
    public int subscriberCount(String conditionId) {
        return subscriberCount.getOrDefault(canonicalConditionId(conditionId), 0);
    }

    /** Emits payloads to any test consumer of the outbound sink. */
    public Flux<String> outboundFlux() {
        return outbound.asFlux();
    }
}
