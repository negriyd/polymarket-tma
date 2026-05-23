package com.polymarket.tma.trading;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polymarket.tma.config.AppProperties;
import com.polymarket.tma.trading.dto.OrderDtos;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.web3j.crypto.StructuredDataEncoder;
import org.web3j.utils.Numeric;

/**
 * Builds the EIP-712 typed data for a Polymarket CLOB <strong>v2</strong> order so the client
 * (Privy wallet) can sign it.
 *
 * <p>Polymarket migrated their CLOB to a new order schema on 2026-04-22; the legacy v1 schema
 * (with {@code nonce}, {@code expiration}, {@code feeRateBps}, {@code taker}) is rejected with
 * {@code 400 "order_version_mismatch"} since 2026-04-30. This builder follows the v2 reference
 * implementation in <a href="https://github.com/Polymarket/py-clob-client-v2">py-clob-client-v2</a>:
 *
 * <pre>
 * Order {
 *   uint256 salt;
 *   address maker;
 *   address signer;
 *   uint256 tokenId;
 *   uint256 makerAmount;
 *   uint256 takerAmount;
 *   uint8   side;
 *   uint8   signatureType;
 *   uint256 timestamp;   // milliseconds, replaces v1 nonce
 *   bytes32 metadata;    // application-defined, default 0x00..00
 *   bytes32 builder;     // builder-attribution code, default 0x00..00
 * }
 * </pre>
 *
 * <p>Domain {@code version} bumped from {@code "1"} to {@code "2"}; {@code verifyingContract}
 * points at the new v2 exchange (or v2 NegRisk exchange for multi-outcome markets). {@code
 * expiration} is no longer signed but still goes on the wire alongside the order — its default
 * {@code "0"} is set in {@link ClobOrderClient}.
 *
 * <p>All {@code uint256} / {@code uint8} fields in {@code domain} and {@code message} are emitted
 * as <strong>decimal strings</strong> so Jackson never loses precision on big token ids and viem
 * / Privy parse values consistently. {@code bytes32} fields are emitted as 0x-prefixed 64-char hex.
 */
@Component
public class OrderBuilder {

    private static final String DOMAIN_NAME = "Polymarket CTF Exchange";
    /** Bumped from "1" in the v1 schema; v2 contracts validate this hash. */
    private static final String DOMAIN_VERSION = "2";
    private static final long CHAIN_ID_POLYGON = 137L;
    private static final BigInteger USDC_DECIMALS_FACTOR = BigInteger.valueOf(1_000_000L);          // 6
    private static final BigInteger TOKEN_DECIMALS_FACTOR = BigInteger.valueOf(1_000_000L);         // 6 for CTF outcome tokens
    /** Default for both {@code metadata} and {@code builder} bytes32 fields when absent. */
    private static final String BYTES32_ZERO = "0x" + "0".repeat(64);

    private static final SecureRandom RNG = new SecureRandom();

    private final AppProperties props;
    private final ObjectMapper objectMapper;

    public OrderBuilder(AppProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
    }

    /** Backwards-compatible single-address overload — both maker and signer set to the wallet (EOA). */
    public BuiltOrder build(String walletAddress, OrderDtos.PrepareOrderRequest req) {
        return build(walletAddress, walletAddress, req, false);
    }

    /** Backwards-compatible overload assuming the regular (non-negRisk) CTF Exchange. */
    public BuiltOrder build(String makerAddress, String signerAddress, OrderDtos.PrepareOrderRequest req) {
        return build(makerAddress, signerAddress, req, false);
    }

    /**
     * Build an EIP-712 v2 order with explicit {@code maker} and {@code signer} addresses.
     *
     * <p>For {@link OrderDtos.SignatureType#EOA} both should be the same wallet. For
     * {@code POLY_PROXY} or {@code POLY_GNOSIS_SAFE}, {@code maker} is the proxy/Safe address that
     * holds funds while {@code signer} is the EOA actually performing the signature.
     *
     * <p>{@code negRisk} selects the EIP-712 {@code verifyingContract}: regular CTF Exchange v2
     * when {@code false}, NegRisk CTF Exchange v2 when {@code true}. Polymarket validates the
     * contract address embedded in the signed digest against the market's actual exchange and
     * returns {@code 400 "order_version_mismatch"} on a miss.
     */
    public BuiltOrder build(String makerAddress, String signerAddress, OrderDtos.PrepareOrderRequest req, boolean negRisk) {
        // Salt MUST stay below JS Number.MAX_SAFE_INTEGER (2^53). Polymarket CLOB parses the
        // POST /order JSON with the standard JSON parser, which represents JSON numbers as
        // float64; a 96-bit salt would silently lose precision on their side, the reconstructed
        // EIP-712 hash would no longer match our signature, and the server would reject the
        // submission as {"error":"Invalid order payload"}. py-clob-client-v2 generates salts via
        // `random.random() * time_ms()` — capped well under 2^53. We use 48 bits (~2.8e14).
        BigInteger salt = BigInteger.valueOf(RNG.nextLong() & 0x0000_FFFF_FFFF_FFFFL);
        long timestampMs = Instant.now().toEpochMilli();
        // Expiration is API-only in v2 (NOT part of the EIP-712 struct). Default "0" means
        // never expire; we plumb req.expiration() through if the caller asked for it.
        long expirationSec = req.expiration() != null ? req.expiration() : 0L;

        int side = req.side() == OrderDtos.Side.BUY ? 0 : 1;
        OrderDtos.SignatureType sigType = req.signatureType() == null ? OrderDtos.SignatureType.EOA : req.signatureType();
        int signatureType = switch (sigType) {
            case EOA -> 0;
            case POLY_PROXY -> 1;
            case POLY_GNOSIS_SAFE -> 2;
        };

        BigInteger sizeShares = req.size().multiply(new BigDecimal(TOKEN_DECIMALS_FACTOR)).toBigInteger();
        BigInteger usdcAmount = req.size().multiply(req.price()).multiply(new BigDecimal(USDC_DECIMALS_FACTOR)).toBigInteger();

        BigInteger makerAmount;
        BigInteger takerAmount;
        if (req.side() == OrderDtos.Side.BUY) {
            makerAmount = usdcAmount;
            takerAmount = sizeShares;
        } else {
            makerAmount = sizeShares;
            takerAmount = usdcAmount;
        }

        Map<String, Object> domain = new LinkedHashMap<>();
        domain.put("name", DOMAIN_NAME);
        domain.put("version", DOMAIN_VERSION);
        // Privy's TypedMessage validator requires chainId as a JSON number (uint256 still per spec).
        domain.put("chainId", CHAIN_ID_POLYGON);
        domain.put("verifyingContract",
                negRisk ? props.polygon().negRiskCtfExchangeAddress() : props.polygon().ctfExchangeAddress());

        // Field order MUST match CTF_EXCHANGE_V2_ORDER_STRUCT in py-clob-client-v2 — the digest
        // is order-sensitive even though the keys are named.
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("salt", u256Decimal(salt));
        message.put("maker", makerAddress);
        message.put("signer", signerAddress);
        message.put("tokenId", u256Decimal(req.tokenId()));
        message.put("makerAmount", u256Decimal(makerAmount));
        message.put("takerAmount", u256Decimal(takerAmount));
        message.put("side", u8Decimal(side));
        message.put("signatureType", u8Decimal(signatureType));
        message.put("timestamp", u256Decimal(timestampMs));
        message.put("metadata", BYTES32_ZERO);
        message.put("builder", BYTES32_ZERO);

        Map<String, Object> types = new LinkedHashMap<>();
        types.put("EIP712Domain", List.of(
                Map.of("name", "name", "type", "string"),
                Map.of("name", "version", "type", "string"),
                Map.of("name", "chainId", "type", "uint256"),
                Map.of("name", "verifyingContract", "type", "address")));
        types.put("Order", List.of(
                Map.of("name", "salt", "type", "uint256"),
                Map.of("name", "maker", "type", "address"),
                Map.of("name", "signer", "type", "address"),
                Map.of("name", "tokenId", "type", "uint256"),
                Map.of("name", "makerAmount", "type", "uint256"),
                Map.of("name", "takerAmount", "type", "uint256"),
                Map.of("name", "side", "type", "uint8"),
                Map.of("name", "signatureType", "type", "uint8"),
                Map.of("name", "timestamp", "type", "uint256"),
                Map.of("name", "metadata", "type", "bytes32"),
                Map.of("name", "builder", "type", "bytes32")));

        Map<String, Object> typedData = new LinkedHashMap<>();
        typedData.put("types", types);
        typedData.put("primaryType", "Order");
        typedData.put("domain", domain);
        typedData.put("message", message);

        // wireOrder = the signed-message fields PLUS the API-only `expiration` field. When the
        // wallet returns its signature, ClobOrderClient.submit() clones this map, sets `signature`
        // and `side`/`salt`/`signatureType` to the API-expected JSON types, and POSTs.
        Map<String, Object> wireOrder = new LinkedHashMap<>(message);
        wireOrder.put("expiration", u256Decimal(expirationSec));

        String orderHash = eip712DigestHex(typedData);
        return new BuiltOrder(orderHash, makerAmount, takerAmount, typedData, wireOrder);
    }

    /**
     * Keccak256 hash of the EIP-712 struct (same digest wallets sign). Hex string with 0x prefix, 66 chars.
     */
    private String eip712DigestHex(Map<String, Object> typedData) {
        try {
            String json = objectMapper.writeValueAsString(typedData);
            byte[] digest = new StructuredDataEncoder(json).hashStructuredData();
            return Numeric.toHexString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute EIP-712 digest", e);
        }
    }

    private static String u256Decimal(BigInteger v) {
        return v.toString();
    }

    private static String u256Decimal(long v) {
        return Long.toString(v);
    }

    /** {@code tokenId} from API: decimal digits only, may exceed signed 64-bit. */
    private static String u256Decimal(String rawTokenId) {
        return new BigInteger(rawTokenId.trim()).toString();
    }

    /** {@code uint8} as decimal string so JSON has no numeric ambiguity for wallets. */
    private static String u8Decimal(int v) {
        if (v < 0 || v > 255) {
            throw new IllegalArgumentException("uint8 out of range: " + v);
        }
        return Integer.toString(v);
    }

    /**
     * Snapshot of a prepared order kept in Redis until the wallet posts its signature back.
     *
     * <p>{@link #wireOrder} mirrors {@link #typedData}'s {@code message} plus the v2 API-only
     * {@code expiration} field. {@link ClobOrderClient} clones it, attaches the wallet signature,
     * and POSTs.
     *
     * <p>Annotated with {@link JsonCreator}/{@link JsonProperty} so Jackson can rebuild the record
     * from JSON in any classpath setup — independent of whether {@code -parameters} is on or
     * whether the global {@link ObjectMapper} has default typing enabled. {@link Serializable} is
     * cheap insurance for any caller that round-trips through Java serialization.
     */
    public record BuiltOrder(
            @JsonProperty("orderHash") String orderHash,
            @JsonProperty("makerAmount") BigInteger makerAmount,
            @JsonProperty("takerAmount") BigInteger takerAmount,
            @JsonProperty("typedData") Map<String, Object> typedData,
            @JsonProperty("wireOrder") Map<String, Object> wireOrder) implements Serializable {

        @JsonCreator
        public BuiltOrder(
                @JsonProperty("orderHash") String orderHash,
                @JsonProperty("makerAmount") BigInteger makerAmount,
                @JsonProperty("takerAmount") BigInteger takerAmount,
                @JsonProperty("typedData") Map<String, Object> typedData,
                @JsonProperty("wireOrder") Map<String, Object> wireOrder) {
            this.orderHash = orderHash;
            this.makerAmount = makerAmount;
            this.takerAmount = takerAmount;
            this.typedData = typedData;
            this.wireOrder = wireOrder;
        }
    }
}
