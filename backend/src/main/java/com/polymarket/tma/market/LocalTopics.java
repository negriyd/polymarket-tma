package com.polymarket.tma.market;

import java.util.List;
import java.util.Map;

/**
 * Gamma tags of markets about the places an app language is spoken (tag ids from
 * {@code /tags/slug/<slug>}). Languages without a tag that has open markets (vi, th, en) get none.
 */
final class LocalTopics {

    private static final String BRAZIL = "100185";
    private static final String MEXICO = "956";
    private static final String ARGENTINA = "167";
    private static final String SPAIN = "1382";
    private static final String GERMANY = "100244";
    private static final String FRANCE = "1378";
    private static final String ITALY = "1370";
    private static final String POLAND = "1383";
    private static final String INDONESIA = "181";
    private static final String PHILIPPINES = "101748";
    private static final String JAPAN = "101522";
    private static final String INDIA = "518";
    private static final String CRICKET = "517";
    private static final String CHINA = "303";
    private static final String UKRAINE = "96";
    private static final String RUSSIA = "95";

    private static final Map<String, List<String>> TAGS = Map.ofEntries(
            Map.entry("pt", List.of(BRAZIL)),
            Map.entry("es", List.of(MEXICO, ARGENTINA, SPAIN)),
            Map.entry("de", List.of(GERMANY)),
            Map.entry("fr", List.of(FRANCE)),
            Map.entry("it", List.of(ITALY)),
            Map.entry("pl", List.of(POLAND)),
            Map.entry("id", List.of(INDONESIA)),
            Map.entry("tl", List.of(PHILIPPINES)),
            Map.entry("ja", List.of(JAPAN)),
            Map.entry("hi", List.of(INDIA, CRICKET)),
            Map.entry("bn", List.of(CRICKET, INDIA)),
            Map.entry("zh", List.of(CHINA)),
            Map.entry("uk", List.of(UKRAINE)),
            Map.entry("ru", List.of(RUSSIA, UKRAINE)));

    private LocalTopics() {}

    static List<String> tagsFor(String locale) {
        return locale == null ? List.of() : TAGS.getOrDefault(locale, List.of());
    }
}
