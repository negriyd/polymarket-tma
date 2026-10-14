package com.polymarket.tma.watch;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Every translation of the bot texts keeps English's {placeholders} and HTML tags. */
class BotTextsPlaceholdersTest {

    static final List<String> LOCALES = List.of("ru", "uk", "es", "pt", "zh", "de", "fr", "it", "pl", "id", "tl",
            "vi", "hi", "bn", "th", "ja");
    private static final Pattern SLOT = Pattern.compile("\\{\\w+}|</?b>");

    static TreeSet<String> slots(String s) {
        TreeSet<String> out = new TreeSet<>();
        Matcher m = SLOT.matcher(s);
        while (m.find()) {
            out.add(m.group());
        }
        return out;
    }

    static void sameSlots(Record en, Record tr, String locale) throws Exception {
        assertThat(tr).as(locale + " is translated").isNotSameAs(en);
        for (RecordComponent c : en.getClass().getRecordComponents()) {
            String e = (String) c.getAccessor().invoke(en);
            String t = (String) c.getAccessor().invoke(tr);
            assertThat(slots(t)).as(locale + "." + c.getName()).isEqualTo(slots(e));
        }
    }

    @Test
    void watchTexts() throws Exception {
        for (String l : LOCALES) {
            sameSlots(WatchTexts.of(null), WatchTexts.of(l), l);
        }
    }
}
