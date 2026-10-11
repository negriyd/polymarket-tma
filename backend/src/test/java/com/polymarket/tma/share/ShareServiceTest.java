package com.polymarket.tma.share;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.polymarket.tma.common.ApiException;
import com.polymarket.tma.market.dto.MarketDto;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShareServiceTest {

    private static MarketDto market(String id) {
        return new MarketDto(id, "0xcid", "Will the ceasefire hold through Oct 31?", null, null, null,
                "https://img/x.jpg", null, null, null, null, true, false, true, false, null, null, null,
                List.of("Yes", "No"), List.of("1", "2"), List.of("0.78", "0.22"), true);
    }

    @Test
    void startParamFitsTelegramRules() {
        assertThat(ShareService.startParam(market("559652"), null)).isEqualTo("m_559652");
        assertThat(ShareService.startParam(market("559652"), "yes")).isEqualTo("m_559652_y");
        assertThat(ShareService.startParam(market("559652"), "no")).isEqualTo("m_559652_n")
                .matches("[A-Za-z0-9_-]{1,64}");
        assertThatThrownBy(() -> ShareService.startParam(market(null), null)).isInstanceOf(ApiException.class);
    }

    @Test
    void plainTextShowsOddsAndTheSharersPickInTheirLanguage() {
        assertThat(ShareService.plainText(market("1"), null, ShareTexts.of(null)))
                .isEqualTo("📊 Will the ceasefire hold through Oct 31?\nYes 78% · No 22%");
        assertThat(ShareService.plainText(market("1"), "yes", ShareTexts.of("ru")))
                .isEqualTo("📊 Will the ceasefire hold through Oct 31?\nДа 78% · Нет 22%\n\n"
                        + "Мой прогноз: Да\nА ты как думаешь?");
        assertThat(ShareTexts.of("de")).isEqualTo(ShareTexts.of(null));
    }
}
