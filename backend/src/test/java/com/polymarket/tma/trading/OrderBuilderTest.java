package com.polymarket.tma.trading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polymarket.tma.config.AppProperties;
import com.polymarket.tma.trading.dto.OrderDtos;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.crypto.StructuredDataEncoder;
import org.web3j.utils.Numeric;

@ExtendWith(MockitoExtension.class)
class OrderBuilderTest {

    /** Polymarket v2 standard CTF Exchange. v1 is dead-on-arrival since the 2026-04-22 migration. */
    private static final String EXCHANGE =
            "0xE111180000d2663C0091e4f400237545B87B996B";
    /** Polymarket v2 NegRisk CTF Exchange (multi-outcome neg-risk markets). */
    private static final String NEG_RISK_EXCHANGE =
            "0xe2222d279d744050d28e00520010520000310F59";
    private static final String BYTES32_ZERO = "0x" + "0".repeat(64);
    /** &gt; 2^53 — must not be rounded if ever sent as JSON number */
    private static final String HUGE_TOKEN_ID =
            "13915689317269078219168496739008737517740566192006337297676041270492637394586";

    @Mock
    private AppProperties props;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private OrderBuilder orderBuilder;

    @BeforeEach
    void setUp() {
        when(props.polygon()).thenReturn(
                new AppProperties.Polygon(
                        "https://polygon-rpc.com",
                        "0x2791Bca1f2de4661ED88A30C99A7a9449Aa84174",
                        EXCHANGE,
                        NEG_RISK_EXCHANGE,
                        "0x4D97DCd97eC945f40cF65F87097ACe5EA0476045"));
        orderBuilder = new OrderBuilder(props, objectMapper);
    }

    @Test
    void typedDataMatchesV2Schema() throws Exception {
        var req = new OrderDtos.PrepareOrderRequest(
                "0xabc",
                HUGE_TOKEN_ID,
                OrderDtos.Side.BUY,
                new BigDecimal("0.5"),
                new BigDecimal("10"),
                null,
                OrderDtos.SignatureType.EOA,
                null,
                null);

        OrderBuilder.BuiltOrder built = orderBuilder.build(
                "0x496b8D9f22Eaa44FC9266f7da1B4a51C3EE58227",
                req);

        @SuppressWarnings("unchecked")
        var message = (java.util.Map<String, Object>) built.typedData().get("message");
        @SuppressWarnings("unchecked")
        var domain = (java.util.Map<String, Object>) built.typedData().get("domain");

        assertThat(domain.get("name")).isEqualTo("Polymarket CTF Exchange");
        assertThat(domain.get("version")).as("v2 bumps EIP-712 domain version from \"1\" to \"2\"").isEqualTo("2");
        assertThat(domain.get("chainId")).isInstanceOf(Number.class);
        assertThat(((Number) domain.get("chainId")).longValue()).isEqualTo(137L);
        assertThat(domain.get("verifyingContract")).isEqualTo(EXCHANGE);

        // v2 EIP-712 struct is exactly these 11 fields (no nonce/taker/expiration/feeRateBps).
        assertThat(message).containsOnlyKeys(
                "salt", "maker", "signer", "tokenId", "makerAmount", "takerAmount",
                "side", "signatureType", "timestamp", "metadata", "builder");

        assertThat(message.get("salt")).isInstanceOf(String.class);
        assertThat(message.get("tokenId")).isInstanceOf(String.class).isEqualTo(HUGE_TOKEN_ID);
        assertThat(message.get("makerAmount")).isInstanceOf(String.class);
        assertThat(message.get("takerAmount")).isInstanceOf(String.class);
        assertThat(message.get("side")).isInstanceOf(String.class).isEqualTo("0");
        assertThat(message.get("signatureType")).isInstanceOf(String.class).isEqualTo("0");
        assertThat(message.get("timestamp")).isInstanceOf(String.class);
        // Timestamp is milliseconds; should comfortably exceed seconds-scale (post-2001-09).
        assertThat(new java.math.BigInteger((String) message.get("timestamp")))
                .isGreaterThan(java.math.BigInteger.valueOf(1_000_000_000_000L));
        assertThat(message.get("metadata")).isEqualTo(BYTES32_ZERO);
        assertThat(message.get("builder")).isEqualTo(BYTES32_ZERO);

        JsonNode root = objectMapper.readTree(objectMapper.writeValueAsBytes(built.typedData()));
        assertThat(root.get("message").get("tokenId").isTextual()).isTrue();
        assertThat(root.get("message").get("tokenId").asText()).isEqualTo(HUGE_TOKEN_ID);
        // Privy validates chainId as a JSON number — must NOT be a string.
        assertThat(root.get("domain").get("chainId").isNumber()).isTrue();
        assertThat(root.get("domain").get("chainId").asLong()).isEqualTo(137L);
    }

    @Test
    void wireOrderMirrorsMessagePlusExpiration() {
        var req = new OrderDtos.PrepareOrderRequest(
                "0xabc",
                "100",
                OrderDtos.Side.BUY,
                new BigDecimal("0.4"),
                new BigDecimal("1"),
                null,
                OrderDtos.SignatureType.EOA,
                null,
                null);

        OrderBuilder.BuiltOrder built = orderBuilder.build(
                "0x496b8D9f22Eaa44FC9266f7da1B4a51C3EE58227", req);

        @SuppressWarnings("unchecked")
        var message = (java.util.Map<String, Object>) built.typedData().get("message");
        var wire = built.wireOrder();
        assertThat(wire).isNotNull();
        assertThat(wire).containsAllEntriesOf(message);
        // Expiration is API-only in v2 (NOT signed) but still on the wire — default "0".
        assertThat(wire.get("expiration")).isEqualTo("0");
        assertThat(message).doesNotContainKey("expiration");
    }

    @Test
    void orderHashMatchesWeb3jStructuredDataEncoder() throws Exception {
        var req = new OrderDtos.PrepareOrderRequest(
                "0x348cd9adf4f6855f58bd9c6dbf9ff251c4142ef77233a5dc95c65b4b61cd2187",
                HUGE_TOKEN_ID,
                OrderDtos.Side.BUY,
                new BigDecimal("0.135"),
                new BigDecimal("2.747737"),
                null,
                null,
                null,
                null);

        OrderBuilder.BuiltOrder built = orderBuilder.build(
                "0x496b8D9f22Eaa44FC9266f7da1B4a51C3EE58227",
                req);

        assertThat(built.orderHash()).matches("0x[a-f0-9]{64}");

        String json = objectMapper.writeValueAsString(built.typedData());
        byte[] digest = new StructuredDataEncoder(json).hashStructuredData();
        assertThat(Numeric.toHexString(digest)).isEqualTo(built.orderHash());
    }

    @Test
    void sellSideUsesUint8StringOne() {
        var req = new OrderDtos.PrepareOrderRequest(
                "0xabc",
                "100",
                OrderDtos.Side.SELL,
                new BigDecimal("0.4"),
                new BigDecimal("1"),
                null,
                OrderDtos.SignatureType.POLY_PROXY,
                null,
                null);

        OrderBuilder.BuiltOrder built = orderBuilder.build(
                "0x0000000000000000000000000000000000000001",
                req);

        @SuppressWarnings("unchecked")
        var message = (java.util.Map<String, Object>) built.typedData().get("message");
        assertThat(message.get("side")).isEqualTo("1");
        assertThat(message.get("signatureType")).isEqualTo("1");
    }

    @Test
    void proxyFlowKeepsMakerAndSignerSeparate() {
        String signer = "0x0000000000000000000000000000000000000001";
        String proxyMaker = "0x0000000000000000000000000000000000000002";

        var req = new OrderDtos.PrepareOrderRequest(
                "0xabc",
                "100",
                OrderDtos.Side.BUY,
                new BigDecimal("0.4"),
                new BigDecimal("1"),
                null,
                OrderDtos.SignatureType.POLY_PROXY,
                null,
                proxyMaker);

        OrderBuilder.BuiltOrder built = orderBuilder.build(proxyMaker, signer, req);

        @SuppressWarnings("unchecked")
        var message = (java.util.Map<String, Object>) built.typedData().get("message");
        assertThat(message.get("maker")).isEqualTo(proxyMaker);
        assertThat(message.get("signer")).isEqualTo(signer);
        assertThat(message.get("signatureType")).isEqualTo("1");
    }

    /**
     * Regression: salt MUST stay below {@code 2^53}. Polymarket CLOB parses {@code POST /order}
     * JSON with a standard parser that represents JSON numbers as float64; a 96-bit salt loses
     * precision on their side, the reconstructed EIP-712 hash no longer matches our signature,
     * and the server returns {@code "Invalid order payload"}. py-clob-client-v2 caps salts well
     * under {@code 2^53} via {@code random.random() * time_ms()}. We use 48 bits (~2.8e14).
     */
    @Test
    void saltStaysBelowJsNumberMaxSafeInteger() {
        String wallet = "0x0000000000000000000000000000000000000003";
        var req = new OrderDtos.PrepareOrderRequest(
                "0xabc",
                "100",
                OrderDtos.Side.BUY,
                new BigDecimal("0.4"),
                new BigDecimal("1"),
                null,
                OrderDtos.SignatureType.EOA,
                null,
                null);

        java.math.BigInteger maxSafeJsNumber = java.math.BigInteger.valueOf((1L << 53) - 1);

        for (int i = 0; i < 100; i++) {
            OrderBuilder.BuiltOrder built = orderBuilder.build(wallet, req);
            @SuppressWarnings("unchecked")
            var message = (java.util.Map<String, Object>) built.typedData().get("message");
            java.math.BigInteger salt = new java.math.BigInteger((String) message.get("salt"));
            assertThat(salt.signum()).as("salt is unsigned").isGreaterThanOrEqualTo(0);
            assertThat(salt).as("salt must fit in JS Number.MAX_SAFE_INTEGER")
                    .isLessThanOrEqualTo(maxSafeJsNumber);
        }
    }

    /**
     * Regression: orders for negRisk markets must be signed against the NegRisk CTF Exchange
     * v2 contract, otherwise CLOB returns {@code 400 "order_version_mismatch"}.
     */
    @Test
    void negRiskTrueSwitchesVerifyingContract() {
        var req = new OrderDtos.PrepareOrderRequest(
                "0xabc",
                "100",
                OrderDtos.Side.BUY,
                new BigDecimal("0.4"),
                new BigDecimal("1"),
                null,
                OrderDtos.SignatureType.EOA,
                null,
                null);

        OrderBuilder.BuiltOrder regular = orderBuilder.build(
                "0x0000000000000000000000000000000000000003",
                "0x0000000000000000000000000000000000000003",
                req,
                false);
        OrderBuilder.BuiltOrder negRisk = orderBuilder.build(
                "0x0000000000000000000000000000000000000003",
                "0x0000000000000000000000000000000000000003",
                req,
                true);

        @SuppressWarnings("unchecked")
        var regularDomain = (java.util.Map<String, Object>) regular.typedData().get("domain");
        @SuppressWarnings("unchecked")
        var negRiskDomain = (java.util.Map<String, Object>) negRisk.typedData().get("domain");
        assertThat(regularDomain.get("verifyingContract")).isEqualTo(EXCHANGE);
        assertThat(negRiskDomain.get("verifyingContract")).isEqualTo(NEG_RISK_EXCHANGE);
        // v2 NegRisk reuses the standard "Polymarket CTF Exchange" / "2" domain — only the
        // verifyingContract differs. (v1 used a separate "Polymarket Neg Risk CTF Exchange" name.)
        assertThat(regularDomain.get("name")).isEqualTo(negRiskDomain.get("name"));
        assertThat(regularDomain.get("version")).isEqualTo("2");
        assertThat(negRiskDomain.get("version")).isEqualTo("2");
    }

    @Test
    void eoaConvenienceOverloadCopiesAddressIntoMakerAndSigner() {
        String wallet = "0x0000000000000000000000000000000000000003";
        var req = new OrderDtos.PrepareOrderRequest(
                "0xabc",
                "100",
                OrderDtos.Side.BUY,
                new BigDecimal("0.4"),
                new BigDecimal("1"),
                null,
                OrderDtos.SignatureType.EOA,
                null,
                null);

        OrderBuilder.BuiltOrder built = orderBuilder.build(wallet, req);

        @SuppressWarnings("unchecked")
        var message = (java.util.Map<String, Object>) built.typedData().get("message");
        assertThat(message.get("maker")).isEqualTo(wallet);
        assertThat(message.get("signer")).isEqualTo(wallet);
    }
}
