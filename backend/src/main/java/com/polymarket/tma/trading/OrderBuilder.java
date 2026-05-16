package com.polymarket.tma.trading;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polymarket.tma.config.AppProperties;
import com.polymarket.tma.trading.dto.OrderDtos;
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
 * Builds the EIP-712 typed data for a Polymarket CLOB order so the client (Privy wallet) can sign it.
 *
 * <p>All {@code uint256} / {@code uint8} fields in {@code domain} and {@code message} are emitted as
 * <strong>decimal strings</strong> so JSON serialization never loses precision on large token ids and
 * viem / Privy parse values consistently.
 *
 * <p>Order schema follows the CTF Exchange contract:
 * <pre>
 * Order {
 *   uint256 salt;
 *   address maker;
 *   address signer;
 *   address taker;
 *   uint256 tokenId;
 *   uint256 makerAmount;
 *   uint256 takerAmount;
 *   uint256 expiration;
 *   uint256 nonce;
 *   uint256 feeRateBps;
 *   uint8   side;
 *   uint8   signatureType;
 * }
 * </pre>
 */
@Component
public class OrderBuilder {

    private static final String DOMAIN_NAME = "Polymarket CTF Exchange";
    private static final String DOMAIN_VERSION = "1";
    private static final long CHAIN_ID_POLYGON = 137L;
    private static final BigInteger USDC_DECIMALS_FACTOR = BigInteger.valueOf(1_000_000L);          // 6
    private static final BigInteger TOKEN_DECIMALS_FACTOR = BigInteger.valueOf(1_000_000L);         // 6 for CTF outcome tokens

    private static final SecureRandom RNG = new SecureRandom();

    private final AppProperties props;
    private final ObjectMapper objectMapper;

    public OrderBuilder(AppProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
    }

    /** Backwards-compatible single-address overload — both maker and signer set to the wallet (EOA). */
    public BuiltOrder build(String walletAddress, OrderDtos.PrepareOrderRequest req) {
        return build(walletAddress, walletAddress, req);
    }

    /**
     * Build an EIP-712 order with explicit {@code maker} and {@code signer} addresses.
     *
     * <p>For {@link OrderDtos.SignatureType#EOA} both should be the same wallet. For
     * {@code POLY_PROXY} or {@code POLY_GNOSIS_SAFE}, {@code maker} is the proxy/Safe address that
     * holds funds while {@code signer} is the EOA actually performing the signature.
     */
    public BuiltOrder build(String makerAddress, String signerAddress, OrderDtos.PrepareOrderRequest req) {
        BigInteger salt = new BigInteger(96, RNG);
        long expiration = req.expiration() != null ? req.expiration() : Instant.now().plusSeconds(600).getEpochSecond();
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
        domain.put("chainId", u256Decimal(CHAIN_ID_POLYGON));
        domain.put("verifyingContract", props.polygon().ctfExchangeAddress());

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("salt", u256Decimal(salt));
        message.put("maker", makerAddress);
        message.put("signer", signerAddress);
        message.put("taker", "0x0000000000000000000000000000000000000000");
        message.put("tokenId", u256Decimal(req.tokenId()));
        message.put("makerAmount", u256Decimal(makerAmount));
        message.put("takerAmount", u256Decimal(takerAmount));
        message.put("expiration", u256Decimal(expiration));
        message.put("nonce", u256Decimal(BigInteger.ZERO));
        message.put("feeRateBps", u256Decimal(BigInteger.ZERO));
        message.put("side", u8Decimal(side));
        message.put("signatureType", u8Decimal(signatureType));

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
                Map.of("name", "taker", "type", "address"),
                Map.of("name", "tokenId", "type", "uint256"),
                Map.of("name", "makerAmount", "type", "uint256"),
                Map.of("name", "takerAmount", "type", "uint256"),
                Map.of("name", "expiration", "type", "uint256"),
                Map.of("name", "nonce", "type", "uint256"),
                Map.of("name", "feeRateBps", "type", "uint256"),
                Map.of("name", "side", "type", "uint8"),
                Map.of("name", "signatureType", "type", "uint8")));

        Map<String, Object> typedData = new LinkedHashMap<>();
        typedData.put("types", types);
        typedData.put("primaryType", "Order");
        typedData.put("domain", domain);
        typedData.put("message", message);

        String orderHash = eip712DigestHex(typedData);
        return new BuiltOrder(orderHash, makerAmount, takerAmount, typedData);
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

    public record BuiltOrder(String orderHash, BigInteger makerAmount, BigInteger takerAmount, Map<String, Object> typedData) {}
}
