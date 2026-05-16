package com.polymarket.tma.trading.clob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class ClobL2SignerTest {

    private final ClobL2Signer signer = new ClobL2Signer();

    /** Sanity / reference: matches py-clob-client build_hmac_signature for the same inputs. */
    @Test
    void signatureMatchesManualHmacBase64Url() throws Exception {
        String secretB64Url = Base64.getUrlEncoder()
                .encodeToString("polymarket-test-secret".getBytes(StandardCharsets.UTF_8));
        ClobCredentials creds = new ClobCredentials("key", secretB64Url, "pass");

        long ts = 1_700_000_000L;
        String method = "POST";
        String path = "/order";
        String body = "{\"hello\":\"world\"}";

        ClobL2Signer.Signed signed = signer.sign(creds, ts, method, path, body);

        byte[] key = Base64.getUrlDecoder().decode(secretB64Url);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        byte[] expected = mac.doFinal((ts + method + path + body).getBytes(StandardCharsets.UTF_8));
        String expectedB64 = Base64.getUrlEncoder().encodeToString(expected);

        assertThat(signed.timestampSec()).isEqualTo(ts);
        assertThat(signed.signature()).isEqualTo(expectedB64);
    }

    @Test
    void signatureChangesWithAnyInput() {
        String secretB64Url = Base64.getUrlEncoder()
                .encodeToString("k".getBytes(StandardCharsets.UTF_8));
        ClobCredentials creds = new ClobCredentials("k", secretB64Url, "p");

        ClobL2Signer.Signed base = signer.sign(creds, 1L, "POST", "/order", "{}");
        assertThat(signer.sign(creds, 2L, "POST", "/order", "{}").signature())
                .isNotEqualTo(base.signature());
        assertThat(signer.sign(creds, 1L, "GET", "/order", "{}").signature())
                .isNotEqualTo(base.signature());
        assertThat(signer.sign(creds, 1L, "POST", "/orders", "{}").signature())
                .isNotEqualTo(base.signature());
        assertThat(signer.sign(creds, 1L, "POST", "/order", "{\"a\":1}").signature())
                .isNotEqualTo(base.signature());
    }

    @Test
    void methodIsCanonicalizedToUpperCase() {
        String secretB64Url = Base64.getUrlEncoder()
                .encodeToString("kk".getBytes(StandardCharsets.UTF_8));
        ClobCredentials creds = new ClobCredentials("k", secretB64Url, "p");

        String lower = signer.sign(creds, 1L, "post", "/x", "").signature();
        String upper = signer.sign(creds, 1L, "POST", "/x", "").signature();
        assertThat(lower).isEqualTo(upper);
    }

    @Test
    void emptyBodyIsSupported() {
        String secretB64Url = Base64.getUrlEncoder()
                .encodeToString("kk".getBytes(StandardCharsets.UTF_8));
        ClobCredentials creds = new ClobCredentials("k", secretB64Url, "p");

        assertThat(signer.sign(creds, 1L, "GET", "/x", null).signature())
                .isEqualTo(signer.sign(creds, 1L, "GET", "/x", "").signature());
    }

    @Test
    void rejectsMissingCredentials() {
        assertThatThrownBy(() -> signer.sign(null, 1L, "POST", "/x", "{}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing");
        assertThatThrownBy(() ->
                signer.sign(new ClobCredentials("k", "", "p"), 1L, "POST", "/x", "{}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing");
    }
}
