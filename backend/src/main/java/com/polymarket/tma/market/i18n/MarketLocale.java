package com.polymarket.tma.market.i18n;

import java.util.Locale;
import java.util.Set;

/** Languages Gamma can translate market titles into (polymarket.com's locale list); English is the source. */
public final class MarketLocale {

    private static final Set<String> SUPPORTED = Set.of(
            "bn", "de", "es", "fr", "hi", "id", "it", "ja", "pl", "pt", "ru", "th", "tl", "uk", "vi", "zh");

    private MarketLocale() {}

    /**
     * First language of an {@code Accept-Language} header ("ru-RU,ru;q=0.9" → "ru"), or {@code null}
     * for English, a missing header or a language Gamma doesn't translate into.
     */
    public static String fromHeader(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return null;
        }
        String first = acceptLanguage.split(",")[0].split(";")[0].trim();
        String base = first.split("[-_]")[0].toLowerCase(Locale.ROOT);
        return SUPPORTED.contains(base) ? base : null;
    }
}
