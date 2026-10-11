package com.polymarket.tma.share;

import java.util.Map;

/** The few strings of a shared market card, per app locale (English fallback). */
public final class ShareTexts {

    public record Texts(String yes, String no, String myPick, String yourTurn, String open) {}

    private static final Texts EN = new Texts("Yes", "No", "My forecast: %s", "What do you think?", "Open market");

    private static final Map<String, Texts> BY_LOCALE = Map.of(
            "ru", new Texts("Да", "Нет", "Мой прогноз: %s", "А ты как думаешь?", "Открыть рынок"),
            "uk", new Texts("Так", "Ні", "Мій прогноз: %s", "А ти як вважаєш?", "Відкрити ринок"),
            "es", new Texts("Sí", "No", "Mi pronóstico: %s", "¿Y tú qué opinas?", "Abrir mercado"),
            "pt", new Texts("Sim", "Não", "Meu palpite: %s", "E você, o que acha?", "Abrir mercado"),
            "zh", new Texts("是", "否", "我的预测：%s", "你怎么看？", "打开市场"));

    private ShareTexts() {}

    public static Texts of(String locale) {
        return locale == null ? EN : BY_LOCALE.getOrDefault(locale, EN);
    }
}
