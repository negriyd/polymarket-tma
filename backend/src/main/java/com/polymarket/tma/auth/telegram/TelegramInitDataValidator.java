package com.polymarket.tma.auth.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polymarket.tma.common.ApiException;
import com.polymarket.tma.config.AppProperties;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Validates Telegram Mini App {@code initData} per
 * <a href="https://docs.telegram-mini-apps.com/platform/init-data">official spec</a>:
 *
 * <ol>
 *   <li>Parse {@code initData} as urlencoded query string.</li>
 *   <li>Take all fields except {@code hash}, sort alphabetically, join {@code key=value} with {@code \n}.</li>
 *   <li>Compute {@code HMAC_SHA256(bot_token, "WebAppData")} (outer key is the literal "WebAppData").</li>
 *   <li>Compute {@code HMAC_SHA256(data_check_string, secret_key)} and compare with {@code hash} in constant time.</li>
 *   <li>Reject if {@code auth_date} is too old (TTL configurable via {@code app.telegram.init-data-ttl}).</li>
 * </ol>
 */
@Component
public class TelegramInitDataValidator {

    private static final Logger log = LoggerFactory.getLogger(TelegramInitDataValidator.class);
    private static final String HMAC_ALG = "HmacSHA256";
    private static final String WEB_APP_DATA = "WebAppData";

    private final AppProperties props;
    private final ObjectMapper mapper;

    public TelegramInitDataValidator(AppProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
    }

    public TelegramInitData validate(String initDataRaw) {
        if (initDataRaw == null || initDataRaw.isBlank()) {
            throw ApiException.unauthorized("INIT_DATA_EMPTY", "initData is empty");
        }

        // Dev fallback: when no bot token is configured, accept a "mock-" prefixed payload to ease local testing.
        String botToken = props.telegram().botToken();
        if ((botToken == null || botToken.isBlank()) && initDataRaw.startsWith("mock-")) {
            log.warn("BOT_TOKEN is empty - accepting mock initData for local development");
            return mockInitData(initDataRaw);
        }
        if (botToken == null || botToken.isBlank()) {
            throw ApiException.unauthorized("BOT_TOKEN_MISSING", "Telegram bot token is not configured");
        }

        List<String[]> pairs = parsePairs(initDataRaw);
        String receivedHash = null;
        List<String> checkParts = new ArrayList<>(pairs.size());
        for (String[] kv : pairs) {
            if ("hash".equals(kv[0])) {
                receivedHash = kv[1];
            } else {
                checkParts.add(kv[0] + "=" + kv[1]);
            }
        }
        if (receivedHash == null) {
            throw ApiException.unauthorized("INIT_DATA_NO_HASH", "initData hash is missing");
        }
        checkParts.sort(String::compareTo);
        String dataCheckString = String.join("\n", checkParts);

        byte[] secretKey = hmac(WEB_APP_DATA.getBytes(StandardCharsets.UTF_8),
                botToken.getBytes(StandardCharsets.UTF_8));
        byte[] computed = hmac(secretKey, dataCheckString.getBytes(StandardCharsets.UTF_8));

        byte[] received;
        try {
            received = HexFormat.of().parseHex(receivedHash);
        } catch (IllegalArgumentException e) {
            throw ApiException.unauthorized("INIT_DATA_BAD_HASH", "initData hash is not valid hex");
        }
        if (!MessageDigest.isEqual(computed, received)) {
            throw ApiException.unauthorized("INIT_DATA_HASH_MISMATCH", "initData hash mismatch");
        }

        return parsePayload(pairs, receivedHash);
    }

    private TelegramInitData parsePayload(List<String[]> pairs, String hash) {
        TelegramUser user = null;
        Instant authDate = null;
        String queryId = null;
        String startParam = null;
        String chatType = null;
        String chatInstance = null;

        for (String[] kv : pairs) {
            switch (kv[0]) {
                case "user" -> {
                    try {
                        user = mapper.readValue(kv[1], TelegramUser.class);
                    } catch (Exception e) {
                        throw ApiException.unauthorized("INIT_DATA_USER", "user field is not valid JSON");
                    }
                }
                case "auth_date" -> {
                    try {
                        authDate = Instant.ofEpochSecond(Long.parseLong(kv[1]));
                    } catch (NumberFormatException e) {
                        throw ApiException.unauthorized("INIT_DATA_AUTH_DATE", "auth_date is not a number");
                    }
                }
                case "query_id" -> queryId = kv[1];
                case "start_param" -> startParam = kv[1];
                case "chat_type" -> chatType = kv[1];
                case "chat_instance" -> chatInstance = kv[1];
                default -> { /* ignore unknown */ }
            }
        }
        if (user == null) {
            throw ApiException.unauthorized("INIT_DATA_USER_MISSING", "user field missing from initData");
        }
        if (authDate == null) {
            throw ApiException.unauthorized("INIT_DATA_AUTH_DATE_MISSING", "auth_date field missing from initData");
        }
        Duration ttl = props.telegram().initDataTtl();
        if (ttl != null && authDate.plus(ttl).isBefore(Instant.now())) {
            throw ApiException.unauthorized("INIT_DATA_EXPIRED", "initData is older than allowed TTL");
        }
        return new TelegramInitData(user, authDate, queryId, startParam, chatType, chatInstance, hash);
    }

    private static List<String[]> parsePairs(String initData) {
        String[] parts = initData.split("&");
        List<String[]> result = new ArrayList<>(parts.length);
        for (String part : parts) {
            int eq = part.indexOf('=');
            if (eq < 0) {
                throw ApiException.unauthorized("INIT_DATA_BAD_PAIR", "Malformed initData pair");
            }
            String key = URLDecoder.decode(part.substring(0, eq), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8);
            result.add(new String[]{key, value});
        }
        return result;
    }

    private static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(new SecretKeySpec(key, HMAC_ALG));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC failure", e);
        }
    }

    private TelegramInitData mockInitData(String raw) {
        long id = Math.abs(raw.hashCode());
        TelegramUser u = new TelegramUser(id, "Dev", "User", "dev_" + id, "en", false, null, true);
        return new TelegramInitData(u, Instant.now(), null, null, null, null, "mock");
    }
}
