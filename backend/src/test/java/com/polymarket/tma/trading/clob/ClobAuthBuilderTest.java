package com.polymarket.tma.trading.clob;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.StructuredDataEncoder;
import org.web3j.utils.Numeric;

class ClobAuthBuilderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ClobAuthBuilder builder = new ClobAuthBuilder(objectMapper);

    @Test
    void typedDataMatchesClobAuthDomainShape() throws Exception {
        ClobAuthBuilder.BuiltAuth built = builder.build(
                "0x496b8D9f22Eaa44FC9266f7da1B4a51C3EE58227",
                1_700_000_000L,
                42L);

        JsonNode root = objectMapper.readTree(objectMapper.writeValueAsBytes(built.typedData()));

        assertThat(root.get("primaryType").asText()).isEqualTo("ClobAuth");
        assertThat(root.get("domain").get("name").asText()).isEqualTo("ClobAuthDomain");
        assertThat(root.get("domain").get("version").asText()).isEqualTo("1");
        assertThat(root.get("domain").get("chainId").asText()).isEqualTo("137");

        JsonNode message = root.get("message");
        assertThat(message.get("address").asText())
                .isEqualTo("0x496b8D9f22Eaa44FC9266f7da1B4a51C3EE58227");
        assertThat(message.get("timestamp").isTextual()).isTrue();
        assertThat(message.get("timestamp").asText()).isEqualTo("1700000000");
        assertThat(message.get("nonce").isTextual()).isTrue();
        assertThat(message.get("nonce").asText()).isEqualTo("42");
        assertThat(message.get("message").asText())
                .isEqualTo("This message attests that I control the given wallet");
    }

    @Test
    void digestMatchesWeb3jStructuredDataEncoder() throws Exception {
        ClobAuthBuilder.BuiltAuth built = builder.build(
                "0x0000000000000000000000000000000000000001",
                1_710_000_000L,
                7L);

        String json = objectMapper.writeValueAsString(built.typedData());
        byte[] digest = new StructuredDataEncoder(json).hashStructuredData();
        assertThat(Numeric.toHexString(digest)).isEqualTo(built.digestHex());
        assertThat(built.digestHex()).matches("0x[a-f0-9]{64}");
    }

    @Test
    void differentNonceProducesDifferentDigest() {
        ClobAuthBuilder.BuiltAuth a = builder.build("0xabc", 1L, 1L);
        ClobAuthBuilder.BuiltAuth b = builder.build("0xabc", 1L, 2L);
        assertThat(a.digestHex()).isNotEqualTo(b.digestHex());
    }
}
