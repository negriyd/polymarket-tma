package com.polymarket.tma.trading.relayer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polymarket.tma.common.ApiException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Client for the Polymarket Relayer — gasless deposit-wallet operations, port of the
 * deposit-wallet half of {@code @polymarket/builder-relayer-client}.
 *
 * <ul>
 *     <li>{@link #createWallet} — {@code WALLET-CREATE}: deploys the owner's deposit wallet. Needs
 *         no user signature; the address is deterministic ({@link #depositWalletAddress}).</li>
 *     <li>{@link #submitBatch} — {@code WALLET}: executes calls from the deposit wallet, authorised
 *         by the owner's signature over {@link DepositWalletBatch#typedData}.</li>
 * </ul>
 *
 * <p>Submissions return immediately with a {@code transactionID}; poll {@link #getTransaction}
 * until {@link RelayerTransaction#isSuccess()} or {@link RelayerTransaction#isFailure()}.
 */
@Component
public class RelayerClient {

    private static final Logger log = LoggerFactory.getLogger(RelayerClient.class);

    static final String SUBMIT_PATH = "/submit";
    private static final String TYPE_WALLET = "WALLET";
    private static final String TYPE_WALLET_CREATE = "WALLET-CREATE";

    private final WebClient client;
    private final RelayerProperties props;
    private final ObjectMapper objectMapper;

    public RelayerClient(WebClient.Builder builder, RelayerProperties props, ObjectMapper objectMapper) {
        this.client = builder.baseUrl(props.baseUrl()).build();
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RelayerTransaction(
            String transactionID,
            String transactionHash,
            String state,
            String from,
            String proxyAddress
    ) {
        public boolean isSuccess() {
            return "STATE_MINED".equals(state) || "STATE_CONFIRMED".equals(state);
        }

        public boolean isFailure() {
            return "STATE_FAILED".equals(state) || "STATE_INVALID".equals(state);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record NonceResponse(String nonce) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record DeployedResponse(Boolean deployed) {}

    /** Deposit wallet address the factory will (or did) deploy for {@code owner}. */
    public String depositWalletAddress(String owner) {
        return DepositWalletAddress.beacon(owner, props.depositWalletFactory(), props.depositWalletBeacon());
    }

    public Mono<Boolean> isDeployed(String depositWallet) {
        return client.get()
                .uri(b -> b.path("/deployed").queryParam("address", depositWallet).queryParam("type", TYPE_WALLET).build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp -> error(resp.statusCode().value(), resp.bodyToMono(String.class), "DEPLOYED"))
                .bodyToMono(DeployedResponse.class)
                .map(r -> Boolean.TRUE.equals(r.deployed()));
    }

    /** Next {@code WALLET} nonce for batches signed by {@code owner}. */
    public Mono<String> nonce(String owner) {
        return client.get()
                .uri(b -> b.path("/nonce").queryParam("address", owner).queryParam("type", TYPE_WALLET).build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp -> error(resp.statusCode().value(), resp.bodyToMono(String.class), "NONCE"))
                .bodyToMono(NonceResponse.class)
                .map(NonceResponse::nonce);
    }

    public Mono<RelayerTransaction> createWallet(String owner) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", TYPE_WALLET_CREATE);
        body.put("from", owner);
        body.put("to", props.depositWalletFactory());
        return submit(body);
    }

    /**
     * Executes {@code calls} from {@code depositWallet}. {@code nonce} / {@code deadline} /
     * {@code calls} must be exactly what {@code signature} was produced over.
     */
    public Mono<RelayerTransaction> submitBatch(String owner, String depositWallet, String nonce, String deadline,
                                                List<DepositWalletBatch.Call> calls, String signature) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("depositWallet", depositWallet);
        params.put("deadline", deadline);
        params.put("calls", calls.stream().map(DepositWalletBatch::callMap).toList());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", TYPE_WALLET);
        body.put("from", owner);
        body.put("to", props.depositWalletFactory());
        body.put("nonce", nonce);
        body.put("signature", signature);
        body.put("depositWalletParams", params);
        return submit(body);
    }

    /** Latest state of a relayer transaction; empty if the relayer does not know the id (yet). */
    public Mono<RelayerTransaction> getTransaction(String transactionId) {
        return client.get()
                .uri(b -> b.path("/transaction").queryParam("id", transactionId).build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp -> error(resp.statusCode().value(), resp.bodyToMono(String.class), "TRANSACTION"))
                .bodyToFlux(RelayerTransaction.class)
                .next();
    }

    private Mono<RelayerTransaction> submit(Map<String, Object> payload) {
        if (!props.builderConfigured()) {
            return Mono.error(ApiException.internal("RELAYER_NOT_CONFIGURED",
                    "Relayer builder credentials are not configured (app.relayer.builder-*)"));
        }
        String body;
        try {
            body = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return Mono.error(ApiException.internal("RELAYER_BODY_SERIALIZE", "Failed to serialize relayer payload"));
        }
        Map<String, String> auth = BuilderAuthSigner.headers(
                props, Instant.now().getEpochSecond(), "POST", SUBMIT_PATH, body);
        return client.post()
                .uri(SUBMIT_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(h -> auth.forEach(h::set))
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp -> resp.bodyToMono(String.class).defaultIfEmpty("")
                        .map(text -> {
                            log.warn("Relayer rejected {} {}: status={} body={}",
                                    payload.get("type"), payload.get("from"), resp.statusCode().value(), text);
                            return upstream(resp.statusCode().value(), text, "SUBMIT");
                        }))
                .bodyToMono(RelayerTransaction.class)
                .doOnNext(tx -> log.info("Relayer accepted {} from={} id={} state={}",
                        payload.get("type"), payload.get("from"), tx.transactionID(), tx.state()));
    }

    private static Mono<ApiException> error(int status, Mono<String> body, String operation) {
        return body.defaultIfEmpty("").map(text -> upstream(status, text, operation));
    }

    private static ApiException upstream(int status, String body, String operation) {
        return ApiException.upstream("RELAYER_" + operation + "_" + status,
                "Polymarket relayer rejected " + operation.toLowerCase() + ": " + status + " " + body);
    }
}
