package com.polymarket.tma.results;

import static org.assertj.core.api.Assertions.assertThat;

import com.polymarket.tma.market.dto.MarketDto;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResultsTest {

    @Test
    void atMostThreeMessagesADayWithTheRestInOneDigest() {
        assertThat(ResultNotifier.plan(2, 3)).isEqualTo(new ResultNotifier.Plan(2, false));
        assertThat(ResultNotifier.plan(3, 3)).isEqualTo(new ResultNotifier.Plan(3, false));
        assertThat(ResultNotifier.plan(7, 3)).as("2 singles + 1 digest of 5").isEqualTo(new ResultNotifier.Plan(2, true));
        assertThat(ResultNotifier.plan(4, 1)).as("only the digest").isEqualTo(new ResultNotifier.Plan(0, true));
        assertThat(ResultNotifier.plan(4, 0)).isEqualTo(new ResultNotifier.Plan(0, false));
    }

    private static ResultNotifier.Pending pending(String kind, String outcome, String payout) {
        return new ResultNotifier.Pending(1, kind, "0xc", "42", "Will A & B win?", outcome,
                payout == null ? null : new BigDecimal(payout));
    }

    @Test
    void messagesAreLocalizedAndEscaped() {
        var ru = NotificationTexts.of("ru");
        assertThat(ResultNotifier.single(pending("PREDICTION_CORRECT", "Yes", null), ru, "Точность: 8 из 10"))
                .isEqualTo("✅ <b>Прогноз сбылся</b>\n«Will A &amp; B win?» — Да\nТочность: 8 из 10");
        assertThat(ResultNotifier.single(pending("BET_WON", "No", "5"), ru, "ignored"))
                .startsWith("🎉 <b>Ставка выиграла!</b>\n«Will A &amp; B win?» — Нет\nК получению: $5.00.");
        assertThat(ResultNotifier.digest(List.of(pending("PREDICTION_WRONG", "Yes", null),
                pending("BET_WON", "Texas", "12.5")), NotificationTexts.of(null)))
                .isEqualTo("📬 <b>Results of your predictions and bets</b>\n"
                        + "❌ «Will A &amp; B win?» — result: Yes\n🎉 «Will A &amp; B win?» — +$12.50");
    }

    private static MarketDto market(boolean closed, String... prices) {
        return new MarketDto("1", "0xc", "Q?", null, null, null, null, null, null, null, null,
                true, closed, !closed, false, null, null, null, List.of("Yes", "No"), List.of("1", "2"),
                List.of(prices), false);
    }

    @Test
    void winnerIsTheOutcomeSettledAtOne() {
        assertThat(ResultTracker.winnerIndex(market(true, "0", "1"))).isEqualTo(1);
        assertThat(ResultTracker.winnerIndex(market(true, "0.5", "0.5"))).as("closed, no result yet").isNull();
        assertThat(ResultTracker.winnerIndex(market(false, "1", "0"))).as("still open").isNull();
    }
}
