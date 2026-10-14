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

    private static final Map<String, Texts> BY_LOCALE = Map.ofEntries(
            Map.entry("ru", new Texts("Да", "Нет", "Мой прогноз: %s", "А ты как думаешь?", "Открыть рынок",
                    "🎯 Точность прогнозов автора: %d%% (%d из %d)", "шанс", "Polymarket в Telegram")),
            Map.entry("uk", new Texts("Так", "Ні", "Мій прогноз: %s", "А ти як вважаєш?", "Відкрити ринок",
                    "🎯 Точність прогнозів автора: %d%% (%d з %d)", "шанс", "Polymarket у Telegram")),
            Map.entry("es", new Texts("Sí", "No", "Mi pronóstico: %s", "¿Y tú qué opinas?", "Abrir mercado",
                    "🎯 Precisión del autor: %d%% (%d de %d)", "probabilidad", "Polymarket en Telegram")),
            Map.entry("pt", new Texts("Sim", "Não", "Meu palpite: %s", "E você, o que acha?", "Abrir mercado",
                    "🎯 Precisão do autor: %d%% (%d de %d)", "de chance", "Polymarket no Telegram")),
            Map.entry("zh", new Texts("是", "否", "我的预测：%s", "你怎么看？", "打开市场",
                    "🎯 作者预测准确率：%d%%（%d/%d）", "概率", "Telegram 上的 Polymarket")),
            Map.entry("de", new Texts("Ja", "Nein", "Meine Prognose: %s", "Was meinst du?", "Markt öffnen",
                    "🎯 Trefferquote des Autors: %d%% (%d von %d)", "Chance", "Polymarket in Telegram")),
            Map.entry("fr", new Texts("Oui", "Non", "Mon pronostic : %s", "Et toi, t’en penses quoi ?", "Ouvrir le marché",
                    "🎯 Précision des pronostics de l’auteur : %d %% (%d sur %d)", "de chances", "Polymarket dans Telegram")),
            Map.entry("it", new Texts("Sì", "No", "La mia previsione: %s", "E tu cosa ne pensi?", "Apri mercato",
                    "🎯 Precisione delle previsioni dell’autore: %d%% (%d su %d)", "di probabilità", "Polymarket in Telegram")),
            Map.entry("pl", new Texts("Tak", "Nie", "Moja prognoza: %s", "A ty jak myślisz?", "Otwórz rynek",
                    "🎯 Trafność prognoz autora: %d%% (%d z %d)", "szans", "Polymarket w Telegramie")),
            Map.entry("id", new Texts("Ya", "Tidak", "Prediksiku: %s", "Kalau kamu, bagaimana?", "Buka pasar",
                    "🎯 Akurasi prediksi penulis: %d%% (%d dari %d)", "peluang", "Polymarket di Telegram")),
            Map.entry("tl", new Texts("Oo", "Hindi", "Hula ko: %s", "Ikaw, ano sa tingin mo?", "Buksan ang market",
                    "🎯 Accuracy ng hula ng author: %d%% (%d sa %d)", "tsansa", "Polymarket sa Telegram")),
            Map.entry("vi", new Texts("Có", "Không", "Dự đoán của tôi: %s", "Còn bạn nghĩ sao?", "Mở thị trường",
                    "🎯 Độ chính xác dự đoán của tác giả: %d%% (%d/%d)", "khả năng", "Polymarket trong Telegram")),
            Map.entry("hi", new Texts("हाँ", "नहीं", "मेरा पूर्वानुमान: %s", "आपको क्या लगता है?", "मार्केट खोलें",
                    "🎯 लेखक के पूर्वानुमान की सटीकता: %1$d%% (%3$d में से %2$d)", "संभावना", "Telegram में Polymarket")),
            Map.entry("bn", new Texts("হ্যাঁ", "না", "আমার পূর্বাভাস: %s", "আপনি কী মনে করেন?", "মার্কেট খুলুন",
                    "🎯 লেখকের পূর্বাভাসের নির্ভুলতা: %1$d%% (%3$dটির মধ্যে %2$dটি)", "সম্ভাবনা", "Telegram-এ Polymarket")),
            Map.entry("th", new Texts("ใช่", "ไม่", "การคาดการณ์ของฉัน: %s", "แล้วคุณคิดอย่างไร?", "เปิดตลาด",
                    "🎯 ความแม่นยำการคาดการณ์ของผู้เขียน: %d%% (%d จาก %d)", "โอกาส", "Polymarket ใน Telegram")),
            Map.entry("ja", new Texts("はい", "いいえ", "私の予想：%s", "あなたはどう思う？", "マーケットを開く",
                    "🎯 作者の予想的中率：%1$d%%（%3$d 件中 %2$d 件）", "確率", "Telegram で Polymarket")));

    private ShareTexts() {}

    /** The accuracy line for a partner's cards, or null when the partner has too few results. */
    public static String authorLine(Texts t, com.polymarket.tma.partner.AuthorService.Accuracy a) {
        return a == null || a.pct() == null ? null : String.format(t.authorAccuracy(), a.pct(), a.correct(), a.resolved());
    }

    public static Texts of(String locale) {
        return locale == null ? EN : BY_LOCALE.getOrDefault(locale, EN);
    }
}
