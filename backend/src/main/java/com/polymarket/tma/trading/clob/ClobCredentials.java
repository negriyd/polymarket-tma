package com.polymarket.tma.trading.clob;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;

/**
 * CLOB L2 credentials returned by {@code POST /auth/api-key} on Polymarket.
 *
 * <p>Stored per user. The {@code secret} is a URL-safe base64 string (decode before HMAC).
 */
public record ClobCredentials(
        @JsonProperty("apiKey") String apiKey,
        @JsonProperty("secret") String secret,
        @JsonProperty("passphrase") String passphrase
) implements Serializable {

    @JsonCreator
    public ClobCredentials(
            @JsonProperty("apiKey") String apiKey,
            @JsonProperty("secret") String secret,
            @JsonProperty("passphrase") String passphrase) {
        this.apiKey = apiKey;
        this.secret = secret;
        this.passphrase = passphrase;
    }
}
