package com.polymarket.tma.trading.bridge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.polymarket.tma.common.ApiException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/** Thin client for the Polymarket Bridge API (deposit side only). */
@Component
public class BridgeClient {

    private static final String BYTES32 = "^0x[0-9a-fA-F]{64}$";

    private final WebClient client;
    private final BridgeProperties props;

    public BridgeClient(WebClient.Builder builder, BridgeProperties props) {
        this.client = builder.baseUrl(props.baseUrl()).build();
        this.props = props;
    }

    /** Bridge addresses per address family; any of them may be null if the bridge omits it. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DepositAddresses(String evm, String svm, String tron, String btc) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record DepositResponse(DepositAddresses address) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Token(String name, String symbol, String address, Integer decimals) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SupportedAsset(String chainId, String chainName, Token token, Double minCheckoutUsd) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SupportedAssetsResponse(List<SupportedAsset> supportedAssets) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Transaction(
            String fromChainId,
            String fromTokenAddress,
            String fromAmountBaseUnit,
            String toChainId,
            String toTokenAddress,
            String status,
            String txHash,
            Long createdTimeMs) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record StatusResponse(List<Transaction> transactions) {}

    /** {@code POST /deposit}: bridge addresses that credit pUSD to {@code polygonWallet}. */
    public Mono<DepositAddresses> depositAddresses(String polygonWallet) {
        return client.post()
                .uri("/deposit")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(this::builderCode)
                .bodyValue(Map.of("address", polygonWallet))
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp -> error(resp.statusCode().value(), resp.bodyToMono(String.class), "DEPOSIT"))
                .bodyToMono(DepositResponse.class)
                .map(DepositResponse::address);
    }

    /**
     * {@code POST /withdraw}: one-off bridge addresses; pUSD sent to the {@code evm} one from
     * {@code polygonWallet} is swapped and delivered as {@code toTokenAddress} on {@code toChainId}.
     * Polymarket asks not to pre-generate these — call only when the user confirms a withdrawal.
     */
    public Mono<DepositAddresses> withdrawalAddresses(String polygonWallet, String toChainId,
                                                      String toTokenAddress, String recipient) {
        return client.post()
                .uri("/withdraw")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(this::builderCode)
                .bodyValue(Map.of(
                        "address", polygonWallet,
                        "toChainId", toChainId,
                        "toTokenAddress", toTokenAddress,
                        "recipientAddr", recipient))
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp -> error(resp.statusCode().value(), resp.bodyToMono(String.class), "WITHDRAW"))
                .bodyToMono(DepositResponse.class)
                .map(DepositResponse::address);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Quote(
            String estToTokenBaseUnit,
            Double estOutputUsd,
            Long estCheckoutTimeMs,
            String quoteId) {}

    /** {@code POST /quote}: estimated output for bridging {@code fromAmountBaseUnit}. */
    public Mono<Quote> quote(String fromChainId, String fromTokenAddress, String fromAmountBaseUnit,
                             String toChainId, String toTokenAddress, String recipient) {
        return client.post()
                .uri("/quote")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "fromAmountBaseUnit", fromAmountBaseUnit,
                        "fromChainId", fromChainId,
                        "fromTokenAddress", fromTokenAddress,
                        "recipientAddress", recipient,
                        "toChainId", toChainId,
                        "toTokenAddress", toTokenAddress))
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp -> error(resp.statusCode().value(), resp.bodyToMono(String.class), "QUOTE"))
                .bodyToMono(Quote.class);
    }

    public Mono<List<SupportedAsset>> supportedAssets() {
        return client.get()
                .uri("/supported-assets")
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp -> error(resp.statusCode().value(), resp.bodyToMono(String.class), "ASSETS"))
                .bodyToMono(SupportedAssetsResponse.class)
                .map(r -> r.supportedAssets() == null ? List.of() : r.supportedAssets());
    }

    /** First page (newest first) of transfers seen at a bridge address. */
    public Mono<List<Transaction>> recentTransactions(String bridgeAddress) {
        return client.get()
                .uri(b -> b.path("/status/{address}").queryParam("limit", 20).build(bridgeAddress))
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp -> error(resp.statusCode().value(), resp.bodyToMono(String.class), "STATUS"))
                .bodyToMono(StatusResponse.class)
                .map(r -> r.transactions() == null ? List.of() : r.transactions());
    }

    /** A malformed code makes the bridge return 400, so only send a valid bytes32. */
    private void builderCode(HttpHeaders h) {
        String code = props.builderCode();
        if (code != null && code.matches(BYTES32)) {
            h.set("X-Builder-Code", code);
        }
    }

    private static Mono<ApiException> error(int status, Mono<String> body, String operation) {
        return body.defaultIfEmpty("").map(text -> ApiException.upstream("BRIDGE_" + operation + "_" + status,
                "Polymarket bridge rejected " + operation.toLowerCase() + ": " + status + " " + text));
    }
}
