package com.polymarket.tma.trading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
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
        org.mockito.Mockito.lenient().when(props.polygon()).thenReturn(
                new AppProperties.Polygon(
                        "https://polygon-rpc.com",
                        "0x2791Bca1f2de4661ED88A30C99A7a9449Aa84174",
                        EXCHANGE,
                        NEG_RISK_EXCHANGE,
                        "0x4D97DCd97eC945f40cF65F87097ACe5EA0476045"));
        // No builder code: the golden order below was accepted by CLOB with builder = 0x00…00.
        orderBuilder = new OrderBuilder(props, objectMapper, null);
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

    // --- POLY_1271 (Deposit Wallet) -------------------------------------------------------
    // Golden vector: a post-only order that Polymarket CLOB accepted ({"status":"live"}) on
    // 2026-10-11, produced by @polymarket/clob-client-v2 1.2.0 via tools/clob-v2-probe.
    private static final String ACCEPTED_EOA = "0x415aE735eB964da56C11D0a53F0f082b85FcbEA5";
    private static final String ACCEPTED_DEPOSIT_WALLET = "0xa1D17A5546aD9166Cb14071c36a5741d812CbE57";
    private static final String ACCEPTED_TOKEN_ID =
            "44209743674197113266585715184635307448475315082310517438746045703276049306333";
    private static final java.math.BigInteger ACCEPTED_SALT = new java.math.BigInteger("610605137369");
    private static final long ACCEPTED_TIMESTAMP_MS = 1791670958468L;
    /** CLOB's {@code orderID} == the order's EIP-712 digest. */
    private static final String ACCEPTED_ORDER_ID =
            "0x207fe39b479c87b4160370f4bd4e6f9dcc404c0aedd3ece8bbe72b63d34c855b";
    private static final String ACCEPTED_SIGNATURE = "0x"
            + "d708769361f06231cede52eeb8ecdb257180f95613d5ffaaa5f4b1459b52cc9c"
            + "348b9c4a4407edf991c192d7c0136d4481ab131914e2cee366974a6e76ce1c1f1c"
            + "3264e159346253e26a64e00b69032db0e7d32f94628de3e6eecb50304d7af3d2"
            + "71b9a678d9cbc474aef77b2738e64478f2d590d492d748f0569d2f151f85c1f3"
            + "4f726465722875696e743235362073616c742c61646472657373206d616b6572"
            + "2c61646472657373207369676e65722c75696e7432353620746f6b656e49642c"
            + "75696e74323536206d616b6572416d6f756e742c75696e743235362074616b65"
            + "72416d6f756e742c75696e743820736964652c75696e7438207369676e617475"
            + "7265547970652c75696e743235362074696d657374616d702c62797465733332"
            + "206d657461646174612c62797465733332206275696c6465722900ba";

    private OrderBuilder.BuiltOrder buildAcceptedPoly1271Order() {
        var req = new OrderDtos.PrepareOrderRequest(
                "0xabc",
                ACCEPTED_TOKEN_ID,
                OrderDtos.Side.BUY,
                new BigDecimal("0.01"),
                new BigDecimal("5"),
                null,
                OrderDtos.SignatureType.POLY_1271,
                null,
                ACCEPTED_DEPOSIT_WALLET);
        return orderBuilder.build(ACCEPTED_DEPOSIT_WALLET, ACCEPTED_EOA, req, false,
                ACCEPTED_SALT, ACCEPTED_TIMESTAMP_MS);
    }

    @Test
    void poly1271OrderHashMatchesClobOrderId() {
        OrderBuilder.BuiltOrder built = buildAcceptedPoly1271Order();

        assertThat(built.orderHash()).isEqualTo(ACCEPTED_ORDER_ID);
        assertThat(built.wireOrder().get("maker")).isEqualTo(ACCEPTED_DEPOSIT_WALLET);
        assertThat(built.wireOrder().get("signer"))
                .as("POLY_1271: signer is the deposit wallet, not the EOA")
                .isEqualTo(ACCEPTED_DEPOSIT_WALLET);
        assertThat(built.wireOrder().get("signatureType")).isEqualTo("3");
        assertThat(built.wireOrder().get("makerAmount")).isEqualTo("50000");
        assertThat(built.wireOrder().get("takerAmount")).isEqualTo("5000000");
    }

    @Test
    void poly1271WalletSignsTypedDataSignWrapper() throws Exception {
        OrderBuilder.BuiltOrder built = buildAcceptedPoly1271Order();

        assertThat(built.typedData().get("primaryType")).isEqualTo("TypedDataSign");
        @SuppressWarnings("unchecked")
        var message = (java.util.Map<String, Object>) built.typedData().get("message");
        assertThat(message.get("name")).isEqualTo("DepositWallet");
        assertThat(message.get("version")).isEqualTo("1");
        assertThat(message.get("verifyingContract")).isEqualTo(ACCEPTED_DEPOSIT_WALLET);
        var expectedContents = new java.util.LinkedHashMap<>(built.wireOrder());
        expectedContents.remove("expiration");
        assertThat(message.get("contents")).isEqualTo(expectedContents);

        // The inner ECDSA signature of the accepted order must recover to the EOA over the
        // digest of exactly the typed data we hand to Privy.
        byte[] digest = new StructuredDataEncoder(objectMapper.writeValueAsString(built.typedData()))
                .hashStructuredData();
        byte[] sig = Numeric.hexStringToByteArray(ACCEPTED_SIGNATURE.substring(0, 2 + 130));
        var signatureData = new Sign.SignatureData(sig[64],
                java.util.Arrays.copyOfRange(sig, 0, 32), java.util.Arrays.copyOfRange(sig, 32, 64));
        String recovered = "0x" + Keys.getAddress(Sign.signedMessageHashToKey(digest, signatureData));
        assertThat(recovered).isEqualToIgnoringCase(ACCEPTED_EOA);
    }

    @Test
    void poly1271FinalizeSignatureReproducesAcceptedSignatureByteForByte() {
        OrderBuilder.BuiltOrder built = buildAcceptedPoly1271Order();
        String walletSignature = ACCEPTED_SIGNATURE.substring(0, 2 + 130);

        String packed = orderBuilder.finalizeSignature(built, walletSignature);

        assertThat(packed).isEqualTo(ACCEPTED_SIGNATURE);
        assertThat((packed.length() - 2) / 2).isEqualTo(317);
    }

    @Test
    void poly1271RejectsNon65ByteWalletSignature() {
        OrderBuilder.BuiltOrder built = buildAcceptedPoly1271Order();

        assertThatThrownBy(() -> orderBuilder.finalizeSignature(built, ACCEPTED_SIGNATURE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("65-byte");
    }

    @Test
    void finalizeSignatureIsPassThroughForEoa() {
        var req = new OrderDtos.PrepareOrderRequest(
                "0xabc", "100", OrderDtos.Side.BUY, new BigDecimal("0.4"), new BigDecimal("1"),
                null, OrderDtos.SignatureType.EOA, null, null);
        OrderBuilder.BuiltOrder built = orderBuilder.build("0x0000000000000000000000000000000000000003", req);

        assertThat(orderBuilder.finalizeSignature(built, "0xabcdef")).isEqualTo("0xabcdef");
        assertThat(built.typedData().get("primaryType")).isEqualTo("Order");
    }

    @Test
    @SuppressWarnings("unchecked")
    void signsTheBuilderCodeIntoTheOrderForAttribution() {
        String code = "0xFD9550A42E3CED40DAC8AC02744C8376D44A0F5BF0CA6A6F35C6045284C07122";
        var attributed = new OrderBuilder(props, objectMapper,
                new com.polymarket.tma.trading.bridge.BridgeProperties("https://bridge.polymarket.com", code));
        var req = new OrderDtos.PrepareOrderRequest("0xcid", "1", OrderDtos.Side.BUY, new java.math.BigDecimal("0.5"),
                new java.math.BigDecimal("10"), null, OrderDtos.SignatureType.EOA, null, null);

        var built = attributed.build(ACCEPTED_EOA, req);
        var message = (java.util.Map<String, Object>) built.typedData().get("message");
        assertThat(message.get("builder")).isEqualTo(code.toLowerCase());
        assertThat(built.wireOrder().get("builder")).isEqualTo(code.toLowerCase());
        assertThat(built.orderHash()).isNotEqualTo(orderBuilder.build(ACCEPTED_EOA, req).orderHash());
    }

    @Test
    void malformedBuilderCodeFallsBackToZero() {
        assertThat(OrderBuilder.builderCodeOrZero(null)).isEqualTo("0x" + "0".repeat(64));
        assertThat(OrderBuilder.builderCodeOrZero("tma")).isEqualTo("0x" + "0".repeat(64));
    }

    @Test
    void fakBuySpendsTheAmountAndAsksForAtLeastAmountOverCapShares() {
        var req = new OrderDtos.PrepareOrderRequest("0xabc", "1", OrderDtos.Side.BUY, new BigDecimal("0.56"),
                new BigDecimal("8.9285"), OrderDtos.OrderType.FAK, OrderDtos.SignatureType.EOA, null, null,
                new BigDecimal("5"));

        OrderBuilder.BuiltOrder built = orderBuilder.build("0x496b8D9f22Eaa44FC9266f7da1B4a51C3EE58227", req);

        assertThat(built.makerAmount()).isEqualTo(java.math.BigInteger.valueOf(5_000_000));
        assertThat(built.takerAmount()).as("5 / 0.56 = 8.92857… cut to 4 decimals")
                .isEqualTo(java.math.BigInteger.valueOf(8_928_500));
        assertThat(built.orderType()).isEqualTo("FAK");
    }
}
