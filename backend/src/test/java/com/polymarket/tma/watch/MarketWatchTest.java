package com.polymarket.tma.watch;

import static org.assertj.core.api.Assertions.assertThat;

import com.polymarket.tma.watch.MarketWatchService.Alert;
import com.polymarket.tma.watch.MarketWatchService.Item;
import com.polymarket.tma.watch.MarketWatchService.Kind;
import com.polymarket.tma.watch.MarketWatchService.State;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MarketWatchTest {

    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");

    private static Item item(String cid, String price, Instant end, boolean committed) {
        return new Item(cid, "123", "Will A <win>?", "Yes", new BigDecimal(price), end, committed);
    }

    @Test
    void firstSightIsABaselineNotAnAlert() {
        var d = MarketWatchService.decide(List.of(item("0xa", "0.40", null, false)), Map.of(), NOW);
        assertThat(d.alerts()).isEmpty();
        assertThat(d.baselines()).extracting(Item::conditionId).containsExactly("0xa");
    }

    @Test
    void alertsOnTenPointMovesEitherWay() {
        var states = Map.of(
                "0xa", new State(new BigDecimal("0.40"), false),
                "0xb", new State(new BigDecimal("0.40"), false),
                "0xc", new State(new BigDecimal("0.40"), false));
        var d = MarketWatchService.decide(List.of(
                item("0xa", "0.50", null, false),
                item("0xb", "0.30", null, false),
                item("0xc", "0.49", null, false)), states, NOW);
        assertThat(d.alerts()).extracting(a -> a.item().conditionId()).containsExactly("0xa", "0xb");
        assertThat(d.alerts()).allMatch(a -> a.kind() == Kind.MOVED);
    }

    @Test
    void remindsOnceWhenABetOrPredictionClosesWithinADay() {
        Instant soon = NOW.plus(Duration.ofHours(5));
        var fresh = Map.of("0xa", new State(new BigDecimal("0.40"), false),
                "0xs", new State(new BigDecimal("0.40"), false));
        var d = MarketWatchService.decide(List.of(
                item("0xa", "0.41", soon, true),
                item("0xs", "0.41", soon, false),
                item("0xlater", "0.41", NOW.plus(Duration.ofDays(3)), true)), fresh, NOW);
        assertThat(d.alerts()).extracting(a -> a.item().conditionId()).containsExactly("0xa");
        assertThat(d.alerts().get(0).kind()).isEqualTo(Kind.CLOSING);

        var reminded = Map.of("0xa", new State(new BigDecimal("0.40"), true));
        assertThat(MarketWatchService.decide(List.of(item("0xa", "0.41", soon, true)), reminded, NOW).alerts()).isEmpty();
    }

    @Test
    void messageListsMovesThenClosingEscaped() {
        var alerts = List.of(
                new Alert(Kind.MOVED, item("0xa", "0.55", null, false), new BigDecimal("0.40")),
                new Alert(Kind.CLOSING, item("0xb", "0.70", NOW.plus(Duration.ofHours(5)), true), null));
        String text = MarketWatchService.text(alerts, Map.of("0xb", "Будет ли B?"), WatchTexts.of(null), NOW);
        assertThat(text).isEqualTo("""
                📈 <b>The odds moved on your markets</b>
                «Will A &lt;win&gt;?» — Yes: 40% → 55%

                ⏰ <b>Closing soon</b>
                «Будет ли B?» — Yes: 70%, closes in ~5 h""");
    }

    @Test
    void weeklyRankAndRef() {
        var me = new WeeklyDigestService.Week(4, 5);
        var all = List.of(me, new WeeklyDigestService.Week(6, 8), new WeeklyDigestService.Week(9, 9),
                new WeeklyDigestService.Week(4, 4), new WeeklyDigestService.Week(7, 2));
        assertThat(WeeklyDigestService.rank(me, all, 3)).as("two users have more correct with 3+ resolved").isEqualTo(3);
        assertThat(WeeklyDigestService.rank(new WeeklyDigestService.Week(2, 2), all, 3)).isNull();
        assertThat(WeeklyDigestService.weekRef(LocalDate.parse("2026-10-12"))).isEqualTo("2026-W42");
    }
}
