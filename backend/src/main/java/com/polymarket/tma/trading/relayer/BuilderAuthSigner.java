package com.polymarket.tma.trading.relayer;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * {@code POLY_BUILDER_*} headers for relayer requests — port of {@code BuilderSigner} from
 * {@code @polymarket/builder-signing-sdk}. Same HMAC scheme as CLOB L2 auth:
 * {@code urlsafeBase64(HMAC-SHA256(base64Decode(secret), ts + METHOD + path + body))}, padding kept.
 * The body must be the exact string sent on the wire.
 */
final class BuilderAuthSigner {

    private static final String ALG = "HmacSHA256";

    private BuilderAuthSigner() {}

    static Map<String, String> headers(RelayerProperties props, long timestampSec,
                                       String method, String path, String body) {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("POLY_BUILDER_API_KEY", props.builderApiKey());
        h.put("POLY_BUILDER_PASSPHRASE", props.builderPassphrase());
        h.put("POLY_BUILDER_SIGNATURE", signature(props.builderSecret(), timestampSec, method, path, body));
        h.put("POLY_BUILDER_TIMESTAMP", Long.toString(timestampSec));
        return h;
    }

    static String signature(String secret, long timestampSec, String method, String path, String body) {
        // Node's Buffer.from(s, "base64") accepts both alphabets; builder secrets are url-safe.
        byte[] key = Base64.getDecoder().decode(secret.replace('-', '+').replace('_', '/'));
        String message = timestampSec + method.toUpperCase() + path + (body == null ? "" : body);
        try {
            Mac mac = Mac.getInstance(ALG);
            mac.init(new SecretKeySpec(key, ALG));
            return Base64.getUrlEncoder().encodeToString(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute builder HMAC signature", e);
        }
    }
}
