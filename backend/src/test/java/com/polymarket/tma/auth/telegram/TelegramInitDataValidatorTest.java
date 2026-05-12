package com.polymarket.tma.auth.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polymarket.tma.common.ApiException;
import com.polymarket.tma.config.AppProperties;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class TelegramInitDataValidatorTest {

    private static final String BOT_TOKEN = "1234567890:AAH-test-bot-token";

    private final ObjectMapper mapper = new ObjectMapper();

    private TelegramInitDataValidator newValidator(Duration ttl) {
        AppProperties props = new AppProperties(
                new AppProperties.Cors(List.of("*")),
                new AppProperties.Jwt("0123456789012345678901234567890123", Duration.ofMinutes(15), Duration.ofDays(30), "test"),
                new AppProperties.Telegram(BOT_TOKEN, ttl),
                new AppProperties.Polymarket("", "", "", "", Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO),
                new AppProperties.Polygon("", "", ""),
                new AppProperties.Privy("", "")
        );
        return new TelegramInitDataValidator(props, mapper);
    }

    @Test
    void validatesCorrectlySignedInitData() {
        String initData = signedInitData(BOT_TOKEN, Instant.now().getEpochSecond());
        TelegramInitData parsed = newValidator(Duration.ofHours(24)).validate(initData);
        assertThat(parsed.user()).isNotNull();
        assertThat(parsed.user().id()).isEqualTo(42L);
        assertThat(parsed.user().username()).isEqualTo("alice");
    }

    @Test
    void rejectsTamperedHash() {
        String initData = signedInitData(BOT_TOKEN, Instant.now().getEpochSecond());
        String tampered = initData.replaceAll("hash=([0-9a-f]+)", "hash=00deadbeef");
        assertThatThrownBy(() -> newValidator(Duration.ofHours(24)).validate(tampered))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("hash");
    }

    @Test
    void rejectsWhenSignedWithWrongBotToken() {
        String initData = signedInitData("wrong-token", Instant.now().getEpochSecond());
        assertThatThrownBy(() -> newValidator(Duration.ofHours(24)).validate(initData))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsExpiredInitData() {
        long aDayAgo = Instant.now().minus(Duration.ofDays(2)).getEpochSecond();
        String initData = signedInitData(BOT_TOKEN, aDayAgo);
        assertThatThrownBy(() -> newValidator(Duration.ofHours(24)).validate(initData))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("TTL");
    }

    /** Build a valid initData string signed with the given bot token. */
    private static String signedInitData(String botToken, long authDateEpoch) {
        String userJson = "{\"id\":42,\"first_name\":\"Alice\",\"username\":\"alice\",\"language_code\":\"en\"}";
        List<String[]> fields = new ArrayList<>();
        fields.add(new String[]{"auth_date", String.valueOf(authDateEpoch)});
        fields.add(new String[]{"query_id", "q-1"});
        fields.add(new String[]{"user", userJson});
        fields.sort((a, b) -> a[0].compareTo(b[0]));
        StringBuilder check = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) check.append('\n');
            check.append(fields.get(i)[0]).append('=').append(fields.get(i)[1]);
        }
        byte[] secret = hmac("WebAppData".getBytes(StandardCharsets.UTF_8), botToken.getBytes(StandardCharsets.UTF_8));
        byte[] hash = hmac(secret, check.toString().getBytes(StandardCharsets.UTF_8));
        String hashHex = HexFormat.of().formatHex(hash);

        StringBuilder qs = new StringBuilder();
        for (String[] kv : fields) {
            if (qs.length() > 0) qs.append('&');
            qs.append(URLEncoder.encode(kv[0], StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(kv[1], StandardCharsets.UTF_8));
        }
        qs.append("&hash=").append(hashHex);
        return qs.toString();
    }

    private static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
