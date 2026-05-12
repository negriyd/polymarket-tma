package com.polymarket.tma.ws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polymarket.tma.config.AppProperties;
import java.net.URI;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
 * Subscription is refcounted: when the first client subscribes to {@code /topic/market/{conditionId}}
 * we add its asset IDs to the upstream subscription; when the last client unsubscribes we remove them.
 */
@Component
public class MarketStreamPublisher {

    private static final Logger log = LoggerFactory.getLogger(MarketStreamPublisher.class);
    private static final String TOPIC_PREFIX = "/topic/market/";

    private final AppProperties props;
    private final SimpMessagingTemplate broker;
    private final ObjectMapper mapper;

    // conditionId -> active subscriber count
    private final Map<String, Integer> subscriberCount = new ConcurrentHashMap<>();
    // sessionId+subId -> conditionId, for cleanup on unsubscribe
    private final Map<String, String> sessionToTopic = new ConcurrentHashMap<>();
    // Outbound queue to upstream WS
    private final Sinks.Many<String> outbound = Sinks.many().multicast().onBackpressureBuffer(256, false);

    private final AtomicReference<Disposable> upstream = new AtomicReference<>();

    public MarketStreamPublisher(AppProperties props, SimpMessagingTemplate broker, ObjectMapper mapper) {
        this.props = props;
        this.broker = broker;
        this.mapper = mapper;
    }

    /** Connect upstream lazily on first interest. */
    private synchronized void ensureUpstream() {
        if (upstream.get() != null) {
            return;
        }
        ReactorNettyWebSocketClient client = new ReactorNettyWebSocketClient();
        URI uri = URI.create(props.polymarket().wsUrl());

        Disposable d = client.execute(uri, session -> {
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

    /** Forward upstream messages to interested STOMP topics. */
    private void handleUpstreamMessage(String payload) {
        try {
            JsonNode node = mapper.readTree(payload);
            String marketId = node.path("market").asText(null);
            if (marketId == null || marketId.isBlank()) {
                marketId = node.path("condition_id").asText(null);
            }
            if (marketId == null || marketId.isBlank()) {
                return;
            }
            broker.convertAndSend(TOPIC_PREFIX + marketId, payload);
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
        String conditionId = destination.substring(TOPIC_PREFIX.length());
        sessionToTopic.put(sessionKey(sessionId, subId), conditionId);

        int next = subscriberCount.merge(conditionId, 1, Integer::sum);
        if (next == 1) {
            ensureUpstream();
            send(buildSubscribePayload(conditionId, "SUBSCRIBE"));
            log.debug("Upstream SUBSCRIBE market={} (refcount=1)", conditionId);
        }
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
            send(buildSubscribePayload(conditionId, "UNSUBSCRIBE"));
            log.debug("Upstream UNSUBSCRIBE market={} (refcount=0)", conditionId);
        }
    }

    private String buildSubscribePayload(String conditionId, String action) {
        try {
            return mapper.writeValueAsString(Map.of(
                    "type", action,
                    "markets", new HashSet<>(Set.of(conditionId))));
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

    private static String sessionKey(String sessionId, String subId) {
        return sessionId + ":" + subId;
    }

    /** Test/diagnostic helpers. */
    public int subscriberCount(String conditionId) {
        return subscriberCount.getOrDefault(conditionId, 0);
    }

    /** Emits payloads to any test consumer of the outbound sink. */
    public Flux<String> outboundFlux() {
        return outbound.asFlux();
    }
}
