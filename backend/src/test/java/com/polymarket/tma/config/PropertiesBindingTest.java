package com.polymarket.tma.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.polymarket.tma.referral.ReferralProperties;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/**
 * Records with more than one constructor silently fail to bind (the property comes back null and
 * startup dies later). Guards the records the app binds from application.yml.
 */
class PropertiesBindingTest {

    private static Binder binder(Map<String, String> props) {
        return new Binder(new MapConfigurationPropertySource(props));
    }

    @Test
    void telegramBinds() {
        var tg = binder(Map.of(
                "app.telegram.bot-token", "123:abc",
                "app.telegram.init-data-ttl", "PT24H",
                "app.telegram.webhook-url", "https://x/api/telegram/webhook",
                "app.telegram.webhook-secret", "s3cret"))
                .bind("app.telegram", AppProperties.Telegram.class).get();
        assertThat(tg.botToken()).isEqualTo("123:abc");
        assertThat(tg.webhookSecret()).isEqualTo("s3cret");
    }

    @Test
    void referralsBind() {
        var r = binder(Map.of("app.referrals.share-bps", "2500", "app.referrals.duration", "P365D"))
                .bind("app.referrals", ReferralProperties.class).get();
        assertThat(r.shareBps()).isEqualTo(2500);
        assertThat(r.duration()).isEqualTo(Duration.ofDays(365));
    }
}
