package com.polymarket.tma.groups;

import static org.assertj.core.api.Assertions.assertThat;

import com.polymarket.tma.referral.StartParam;
import java.util.List;
import org.junit.jupiter.api.Test;

class GroupServiceTest {

    @Test
    void readsCommandsMeantForUs() {
        assertThat(GroupService.command("/top", "PmBot")).isEqualTo("top");
        assertThat(GroupService.command("/TOP@pmbot extra", "PmBot")).isEqualTo("top");
        assertThat(GroupService.command("/top@otherbot", "PmBot")).isNull();
        assertThat(GroupService.command("hello /top", "PmBot")).isNull();
        assertThat(GroupService.command(null, "PmBot")).isNull();
    }

    @Test
    void boardShowsMedalsThenPlacesEscaped() {
        var t = GroupTexts.of(null);
        String text = GroupService.board(t, "Top", List.of(
                new GroupService.Row("Ann", 5, 6), new GroupService.Row("<b>", 4, 4),
                new GroupService.Row("@cy", 3, 9), new GroupService.Row("Dee", 1, 1)));
        assertThat(text).isEqualTo("""
                Top

                🥇 Ann — 5 of 6
                🥈 &lt;b&gt; — 4 of 4
                🥉 @cy — 3 of 9
                4. Dee — 1 of 1""");
        assertThat(GroupService.board(t, "Top", List.of())).startsWith("Top\n\nNo resolved predictions");
    }

    @Test
    void joinLinkRoundTripsThroughStartParam() {
        String link = GroupService.joinLink("PmBot", -1001234567890L);
        assertThat(link).isEqualTo("https://t.me/PmBot?startapp=g1001234567890_sgroup");
        StartParam sp = StartParam.parse("g1001234567890_sgroup");
        assertThat(sp.groupChat()).isEqualTo(-1001234567890L);
        assertThat(sp.source()).isEqualTo("group");
        assertThat(GroupService.displayName(" ", "neo")).isEqualTo("@neo");
    }
}
