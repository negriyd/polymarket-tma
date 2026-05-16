package com.polymarket.tma.trading.clob;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.web3j.crypto.StructuredDataEncoder;
import org.web3j.utils.Numeric;

/**
 * Builds the EIP-712 typed data required by Polymarket CLOB to derive an API key (L1 step).
 *
 * <p>The wallet signs an {@code ClobAuth} struct under domain {@code ClobAuthDomain}; the signature is then
 * exchanged for {@code (apiKey, secret, passphrase)} at {@code POST /auth/api-key}. The CLOB also accepts the
 * same headers ({@code POLY_ADDRESS / POLY_SIGNATURE / POLY_TIMESTAMP / POLY_NONCE}) on {@code GET /auth/api-key}
 * to recover an existing key for the same address.
 *
 * <p>Schema and naming follow the official Polymarket clob clients (py-clob-client / clob-client TS). All
 * {@code uint256} fields are emitted as decimal strings for consistency with viem / Privy.
 */
@Component
public class ClobAuthBuilder {

    public static final String DOMAIN_NAME = "ClobAuthDomain";
    public static final String DOMAIN_VERSION = "1";
    public static final long CHAIN_ID_POLYGON = 137L;
    public static final String ATTESTATION =
            "This message attests that I control the given wallet";

    private static final SecureRandom RNG = new SecureRandom();

    private final ObjectMapper objectMapper;

    public ClobAuthBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public BuiltAuth build(String walletAddress) {
        long timestampSec = Instant.now().getEpochSecond();
        long nonce = Math.floorMod(RNG.nextLong(), 1_000_000_000L);
        return build(walletAddress, timestampSec, nonce);
    }

    /** Deterministic variant for tests / replay. */
    public BuiltAuth build(String walletAddress, long timestampSec, long nonce) {
        Map<String, Object> domain = new LinkedHashMap<>();
        domain.put("name", DOMAIN_NAME);
        domain.put("version", DOMAIN_VERSION);
        domain.put("chainId", Long.toString(CHAIN_ID_POLYGON));

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("address", walletAddress);
        message.put("timestamp", Long.toString(timestampSec));
        message.put("nonce", Long.toString(nonce));
        message.put("message", ATTESTATION);

        Map<String, Object> types = new LinkedHashMap<>();
        types.put("EIP712Domain", List.of(
                Map.of("name", "name", "type", "string"),
                Map.of("name", "version", "type", "string"),
                Map.of("name", "chainId", "type", "uint256")));
        types.put("ClobAuth", List.of(
                Map.of("name", "address", "type", "address"),
                Map.of("name", "timestamp", "type", "string"),
                Map.of("name", "nonce", "type", "uint256"),
                Map.of("name", "message", "type", "string")));

        Map<String, Object> typedData = new LinkedHashMap<>();
        typedData.put("types", types);
        typedData.put("primaryType", "ClobAuth");
        typedData.put("domain", domain);
        typedData.put("message", message);

        String digest = digestHex(typedData);
        return new BuiltAuth(walletAddress, timestampSec, nonce, typedData, digest);
    }

    private String digestHex(Map<String, Object> typedData) {
        try {
            String json = objectMapper.writeValueAsString(typedData);
            byte[] hash = new StructuredDataEncoder(json).hashStructuredData();
            return Numeric.toHexString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute ClobAuth EIP-712 digest", e);
        }
    }

    public record BuiltAuth(
            String address,
            long timestampSec,
            long nonce,
            Map<String, Object> typedData,
            String digestHex
    ) {}
}
