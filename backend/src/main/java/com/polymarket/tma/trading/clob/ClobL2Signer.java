package com.polymarket.tma.trading.clob;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Computes the L2 HMAC signature Polymarket CLOB expects on authenticated requests.
 *
 * <p>Reference: py-clob-client {@code build_hmac_signature}. Algorithm:
 * <ol>
 *     <li>{@code secret} arrives URL-safe base64 encoded (with padding). Decode to raw bytes.</li>
 *     <li>Concatenate {@code timestamp + method + requestPath + body} (UTF-8).</li>
 *     <li>Sign with HMAC-SHA256 using the decoded secret as key.</li>
 *     <li>Encode the digest with URL-safe base64 (with padding).</li>
 * </ol>
 *
 * <p>{@code body} must be the exact JSON string sent on the wire (or empty string for GET).
 */
@Component
public class ClobL2Signer {

    private static final String ALG = "HmacSHA256";

    public Signed sign(ClobCredentials creds,
                       String httpMethod,
                       String requestPath,
                       String body) {
        long ts = Instant.now().getEpochSecond();
        return sign(creds, ts, httpMethod, requestPath, body);
    }

    public Signed sign(ClobCredentials creds,
                       long timestampSec,
                       String httpMethod,
                       String requestPath,
                       String body) {
        if (creds == null || creds.secret() == null || creds.secret().isBlank()) {
            throw new IllegalStateException("CLOB L2 credentials missing");
        }
        byte[] key = Base64.getUrlDecoder().decode(creds.secret());
        String message = timestampSec + httpMethod.toUpperCase() + requestPath + (body == null ? "" : body);
        try {
            Mac mac = Mac.getInstance(ALG);
            mac.init(new SecretKeySpec(key, ALG));
            byte[] sig = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            String sigB64 = Base64.getUrlEncoder().encodeToString(sig);
            return new Signed(timestampSec, sigB64);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute L2 HMAC signature", e);
        }
    }

    public record Signed(long timestampSec, String signature) {}
}
