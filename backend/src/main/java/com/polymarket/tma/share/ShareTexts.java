package com.polymarket.tma.share;

import java.util.Map;

/** The few strings of a shared market card, per app locale (English fallback). */
public final class ShareTexts {

    /**
     * @param authorAccuracy share card line of a partner: pct, correct, resolved
     * @param chance         "72% chance" on the image card
     * @param tagline        footer of the image card
     */
    public record Texts(String yes, String no, String myPick, String yourTurn, String open,
                        String authorAccuracy, String chance, String tagline) {}

    private static final Texts EN = new Texts("Yes", "No", "My forecast: %s", "What do you think?", "Open market",
            "🎯 Author's forecast accuracy: %d%% (%d of %d)", "chance", "Polymarket in Telegram");

    private static final Map<String, Texts> BY_LOCALE = Map.of(
            "ru", new Texts("Да", "Нет", "Мой прогноз: %s", "А ты как думаешь?", "Открыть рынок",
                    "🎯 Точность прогнозов автора: %d%% (%d из %d)", "шанс", "Polymarket в Telegram"),
            "uk", new Texts("Так", "Ні", "Мій прогноз: %s", "А ти як вважаєш?", "Відкрити ринок",
                    "🎯 Точність прогнозів автора: %d%% (%d з %d)", "шанс", "Polymarket у Telegram"),
            "es", new Texts("Sí", "No", "Mi pronóstico: %s", "¿Y tú qué opinas?", "Abrir mercado",
                    "🎯 Precisión del autor: %d%% (%d de %d)", "probabilidad", "Polymarket en Telegram"),
            "pt", new Texts("Sim", "Não", "Meu palpite: %s", "E você, o que acha?", "Abrir mercado",
                    "🎯 Precisão do autor: %d%% (%d de %d)", "de chance", "Polymarket no Telegram"),
            "zh", new Texts("是", "否", "我的预测：%s", "你怎么看？", "打开市场",
                    "🎯 作者预测准确率：%d%%（%d/%d）", "概率", "Telegram 上的 Polymarket"));

    private ShareTexts() {}

    /** The accuracy line for a partner's cards, or null when the partner has too few results. */
    public static String authorLine(Texts t, com.polymarket.tma.partner.AuthorService.Accuracy a) {
        return a == null || a.pct() == null ? null : String.format(t.authorAccuracy(), a.pct(), a.correct(), a.resolved());
    }

    public static Texts of(String locale) {
        return locale == null ? EN : BY_LOCALE.getOrDefault(locale, EN);
    }
}
