package com.polymarket.tma.trading.relayer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * EIP-712 {@code Batch} the wallet owner (Privy EOA) signs to let the relayer execute calls from
 * their deposit wallet — e.g. the pUSD / CTF trading approvals.
 *
 * <p>Mirrors {@code signBatch} in {@code @polymarket/builder-relayer-client}. Numeric fields are
 * decimal strings and {@code chainId} a JSON number, matching what Privy's typed-data validator
 * expects (same convention as {@code OrderBuilder}).
 */
public final class DepositWalletBatch {

    public static final String DOMAIN_NAME = "DepositWallet";
    public static final String DOMAIN_VERSION = "1";
    private static final long CHAIN_ID_POLYGON = 137L;

    /** One call executed by the deposit wallet. {@code value} is wei as a decimal string. */
    public record Call(String target, String value, String data) {}

    private DepositWalletBatch() {}

    public static Map<String, Object> typedData(String wallet, String nonce, String deadline, List<Call> calls) {
        Map<String, Object> domain = new LinkedHashMap<>();
        domain.put("name", DOMAIN_NAME);
        domain.put("version", DOMAIN_VERSION);
        domain.put("chainId", CHAIN_ID_POLYGON);
        domain.put("verifyingContract", wallet);

        Map<String, Object> types = new LinkedHashMap<>();
        types.put("EIP712Domain", List.of(
                Map.of("name", "name", "type", "string"),
                Map.of("name", "version", "type", "string"),
                Map.of("name", "chainId", "type", "uint256"),
                Map.of("name", "verifyingContract", "type", "address")));
        types.put("Call", List.of(
                Map.of("name", "target", "type", "address"),
                Map.of("name", "value", "type", "uint256"),
                Map.of("name", "data", "type", "bytes")));
        types.put("Batch", List.of(
                Map.of("name", "wallet", "type", "address"),
                Map.of("name", "nonce", "type", "uint256"),
                Map.of("name", "deadline", "type", "uint256"),
                Map.of("name", "calls", "type", "Call[]")));

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("wallet", wallet);
        message.put("nonce", nonce);
        message.put("deadline", deadline);
        message.put("calls", calls.stream().map(DepositWalletBatch::callMap).toList());

        Map<String, Object> typedData = new LinkedHashMap<>();
        typedData.put("types", types);
        typedData.put("primaryType", "Batch");
        typedData.put("domain", domain);
        typedData.put("message", message);
        return typedData;
    }

    static Map<String, Object> callMap(Call c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("target", c.target());
        m.put("value", c.value());
        m.put("data", c.data());
        return m;
    }
}
