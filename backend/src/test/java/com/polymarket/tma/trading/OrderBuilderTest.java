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

    private static final String EXCHANGE =
            "0x4bFb41d5B3570DeFd03C39a9A4D8dE6Bd8B8982E";
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
                        "0x4D97DCd97eC945f40cF65F87097ACe5EA0476045"));
        orderBuilder = new OrderBuilder(props, objectMapper);
    }

    @Test
    void typedDataUsesDecimalStringsForAllUintFields() throws Exception {
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

        assertThat(domain.get("chainId")).isInstanceOf(String.class).isEqualTo("137");

        assertThat(message.get("salt")).isInstanceOf(String.class);
        assertThat(message.get("tokenId")).isInstanceOf(String.class).isEqualTo(HUGE_TOKEN_ID);
        assertThat(message.get("makerAmount")).isInstanceOf(String.class);
        assertThat(message.get("takerAmount")).isInstanceOf(String.class);
        assertThat(message.get("expiration")).isInstanceOf(String.class);
        assertThat(message.get("nonce")).isInstanceOf(String.class).isEqualTo("0");
        assertThat(message.get("feeRateBps")).isInstanceOf(String.class).isEqualTo("0");
        assertThat(message.get("side")).isInstanceOf(String.class).isEqualTo("0");
        assertThat(message.get("signatureType")).isInstanceOf(String.class).isEqualTo("0");

        JsonNode root = objectMapper.readTree(objectMapper.writeValueAsBytes(built.typedData()));
        assertThat(root.get("message").get("tokenId").isTextual()).isTrue();
        assertThat(root.get("message").get("tokenId").asText()).isEqualTo(HUGE_TOKEN_ID);
        assertThat(root.get("domain").get("chainId").isTextual()).isTrue();
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
