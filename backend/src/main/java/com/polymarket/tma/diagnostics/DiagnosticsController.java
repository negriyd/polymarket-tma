package com.polymarket.tma.diagnostics;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Operator-facing diagnostics for Polymarket CLOB connectivity. Polymarket's WAF returns the same
 * "Trading restricted in your region" body for several different rejection conditions (geo, IP
 * reputation, Cloudflare threat score), so when {@code POST /api/orders/submit} starts failing
 * these probes pinpoint the actual failure mode without redeploying.
 *
 * <p>Public on purpose — responses do not include credentials, only the externally visible egress
 * IP and Cloudflare's view of it (both of which are already revealed by any direct outbound HTTP
 * request from the host).
 */
@RestController
@RequestMapping("/api/diagnostics")
public class DiagnosticsController {

    private static final Logger log = LoggerFactory.getLogger(DiagnosticsController.class);

    private final WebClient ifconfigClient;
    private final WebClient clobRootClient;
    private final WebClient cloudflareTraceClient;
    private final WebClient clobOrderClient;

    public DiagnosticsController(WebClient.Builder builder) {
        this.ifconfigClient = builder.clone().baseUrl("https://ifconfig.me").build();
        this.clobRootClient = builder.clone().baseUrl("https://clob.polymarket.com").build();
        this.cloudflareTraceClient = builder.clone().baseUrl("https://www.cloudflare.com").build();
        this.clobOrderClient = builder.clone().baseUrl("https://clob.polymarket.com").build();
    }

    /**
     * GETs {@code https://ifconfig.me/ip} from the same WebClient used for CLOB. Returns the raw
     * IP string the upstream sees:
     * <pre>
     *   curl http://localhost:8080/api/diagnostics/clob-egress-ip
     *   {"egressIp":"159.65.240.42"}
     * </pre>
     */
    @GetMapping(value = "/clob-egress-ip", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> clobEgressIp() {
        Map<String, Object> body = new LinkedHashMap<>();
        try {
            String ip = ifconfigClient.get()
                    .uri("/ip")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(15));
            body.put("egressIp", ip == null ? "" : ip.trim());
            body.put("ok", true);
        } catch (WebClientResponseException e) {
            log.warn("egress-ip probe HTTP {}: {}", e.getStatusCode().value(), e.getResponseBodyAsString());
            body.put("ok", false);
            body.put("error", "HTTP " + e.getStatusCode().value());
            body.put("body", e.getResponseBodyAsString());
        } catch (RuntimeException e) {
            log.warn("egress-ip probe failed", e);
            body.put("ok", false);
            body.put("error", e.toString());
        }
        return body;
    }

    /**
     * GETs CLOB root. Reachability sanity check independent of payloads / auth. Returns
     * {@code "OK"} from a healthy region, blank/timeout otherwise.
     */
    @GetMapping(value = "/clob-reachability", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> clobReachability() {
        Map<String, Object> body = new LinkedHashMap<>();
        try {
            String response = clobRootClient.get()
                    .uri("/")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(15));
            body.put("ok", true);
            body.put("body", response == null ? "" : response.trim());
        } catch (WebClientResponseException e) {
            body.put("ok", false);
            body.put("status", e.getStatusCode().value());
            body.put("body", e.getResponseBodyAsString());
        } catch (RuntimeException e) {
            body.put("ok", false);
            body.put("error", e.toString());
        }
        return body;
    }

    /**
     * Hits Cloudflare's trace endpoint. Returns Cloudflare's own view of country code
     * ({@code loc=}), client IP ({@code ip=}), connecting datacenter ({@code colo=}) and
     * warp/threat status. Polymarket CLOB sits behind Cloudflare, so whatever Cloudflare sees here
     * is exactly what CLOB's WAF rules use for geoblock decisions. Useful for confirming the host
     * is in an allowed region after a redeploy/migration.
     */
    @GetMapping(value = "/cf-trace", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> cloudflareTrace() {
        Map<String, Object> body = new LinkedHashMap<>();
        try {
            String trace = cloudflareTraceClient.get()
                    .uri("/cdn-cgi/trace")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(15));
            body.put("ok", true);
            body.put("trace", trace == null ? "" : trace);
        } catch (WebClientResponseException e) {
            body.put("ok", false);
            body.put("status", e.getStatusCode().value());
            body.put("body", e.getResponseBodyAsString());
        } catch (RuntimeException e) {
            body.put("ok", false);
            body.put("error", e.toString());
        }
        return body;
    }

    /**
     * Sends an empty {@code POST /order}. The body is intentionally invalid, so a healthy CLOB
     * responds with HTTP 400/401/422 (auth or schema error). A geoblocked WAF returns 403 with
     * {@code "Trading restricted in your region"}. This separates "the WAF lets us in, real signing
     * code has a bug" from "WAF rejects us at the network level".
     *
     * <p>Optional {@code ?ua=...} overrides the User-Agent for fingerprinting A/B tests.
     */
    @GetMapping(value = "/clob-post-probe", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> clobPostProbe(@RequestParam(value = "ua", required = false) String userAgent) {
        Map<String, Object> body = new LinkedHashMap<>();
        try {
            WebClient.RequestBodySpec req = clobOrderClient.post()
                    .uri("/order")
                    .header("Content-Type", "application/json");
            if (userAgent != null && !userAgent.isBlank()) {
                req = req.header(HttpHeaders.USER_AGENT, userAgent);
            }
            String response = req
                    .bodyValue("{}")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(15));
            body.put("ok", true);
            body.put("body", response == null ? "" : response);
        } catch (WebClientResponseException e) {
            body.put("ok", false);
            body.put("status", e.getStatusCode().value());
            body.put("body", e.getResponseBodyAsString());
        } catch (RuntimeException e) {
            body.put("ok", false);
            body.put("error", e.toString());
        }
        return body;
    }
}
