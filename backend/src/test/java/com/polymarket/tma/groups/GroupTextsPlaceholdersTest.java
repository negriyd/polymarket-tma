package com.polymarket.tma.groups;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class GroupTextsPlaceholdersTest {

    private static final Pattern SLOT = Pattern.compile("\\{\\w+}|</?b>|/top|/join");

    private static TreeSet<String> slots(String s) {
        TreeSet<String> out = new TreeSet<>();
        Matcher m = SLOT.matcher(s);
        while (m.find()) {
            out.add(m.group());
        }
        return out;
    }

    @Test
    void everyLocaleKeepsSlotsAndThePlacePrefix() throws Exception {
        var en = GroupTexts.of(null);
        for (String l : List.of("ru", "uk", "es", "pt", "zh", "de", "fr", "it", "pl", "id", "tl", "vi", "hi", "bn",
                "th", "ja")) {
            var tr = GroupTexts.of(l);
            assertThat(tr).as(l).isNotSameAs(en);
            for (RecordComponent c : GroupTexts.Texts.class.getRecordComponents()) {
                assertThat(slots((String) c.getAccessor().invoke(tr))).as(l + "." + c.getName())
                        .isEqualTo(slots((String) c.getAccessor().invoke(en)));
            }
            assertThat(tr.topLine()).as(l).startsWith("{i}. ");
        }
    }
}
