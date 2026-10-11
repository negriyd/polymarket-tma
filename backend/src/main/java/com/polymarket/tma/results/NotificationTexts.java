package com.polymarket.tma.results;

import java.util.Map;

/** Bot messages about results, per app locale (English fallback). {q} question, {o} outcome, {p} payout. */
final class NotificationTexts {

    record Texts(String predCorrect, String predWrong, String betWon, String accuracy, String digestTitle,
                 String digestCorrect, String digestWrong, String digestWon,
                 String openMarket, String claim, String moreMarkets, String openApp, String yes, String no) {}

    private static final Texts EN = new Texts(
            "✅ <b>Your prediction came true</b>\n«{q}» — {o}",
            "❌ <b>Your prediction didn't come true</b>\n«{q}» — result: {o}",
            "🎉 <b>Your bet won!</b>\n«{q}» — {o}\nTo collect: ${p}. Claim it on the Wallet tab.",
            "Accuracy: {c} of {t}",
            "📬 <b>Results of your predictions and bets</b>",
            "✅ «{q}» — {o}", "❌ «{q}» — result: {o}", "🎉 «{q}» — +${p}",
            "Open market", "Claim winnings", "More markets", "Open app", "Yes", "No");

    private static final Map<String, Texts> BY_LOCALE = Map.of(
            "ru", new Texts(
                    "✅ <b>Прогноз сбылся</b>\n«{q}» — {o}",
                    "❌ <b>Прогноз не сбылся</b>\n«{q}» — итог: {o}",
                    "🎉 <b>Ставка выиграла!</b>\n«{q}» — {o}\nК получению: ${p}. Заберите выигрыш на вкладке «Кошелёк».",
                    "Точность: {c} из {t}",
                    "📬 <b>Итоги ваших прогнозов и ставок</b>",
                    "✅ «{q}» — {o}", "❌ «{q}» — итог: {o}", "🎉 «{q}» — +${p}",
                    "Открыть рынок", "Забрать выигрыш", "Другие рынки", "Открыть приложение", "Да", "Нет"),
            "uk", new Texts(
                    "✅ <b>Прогноз справдився</b>\n«{q}» — {o}",
                    "❌ <b>Прогноз не справдився</b>\n«{q}» — підсумок: {o}",
                    "🎉 <b>Ставка виграла!</b>\n«{q}» — {o}\nДо отримання: ${p}. Заберіть виграш на вкладці «Гаманець».",
                    "Точність: {c} з {t}",
                    "📬 <b>Підсумки ваших прогнозів і ставок</b>",
                    "✅ «{q}» — {o}", "❌ «{q}» — підсумок: {o}", "🎉 «{q}» — +${p}",
                    "Відкрити ринок", "Забрати виграш", "Інші ринки", "Відкрити застосунок", "Так", "Ні"),
            "es", new Texts(
                    "✅ <b>Tu pronóstico se cumplió</b>\n«{q}» — {o}",
                    "❌ <b>Tu pronóstico no se cumplió</b>\n«{q}» — resultado: {o}",
                    "🎉 <b>¡Tu apuesta ganó!</b>\n«{q}» — {o}\nPor cobrar: ${p}. Cóbralo en la pestaña Billetera.",
                    "Precisión: {c} de {t}",
                    "📬 <b>Resultados de tus pronósticos y apuestas</b>",
                    "✅ «{q}» — {o}", "❌ «{q}» — resultado: {o}", "🎉 «{q}» — +${p}",
                    "Abrir mercado", "Cobrar ganancias", "Más mercados", "Abrir la app", "Sí", "No"),
            "pt", new Texts(
                    "✅ <b>Seu palpite se confirmou</b>\n«{q}» — {o}",
                    "❌ <b>Seu palpite não se confirmou</b>\n«{q}» — resultado: {o}",
                    "🎉 <b>Sua aposta ganhou!</b>\n«{q}» — {o}\nA receber: ${p}. Resgate na aba Carteira.",
                    "Precisão: {c} de {t}",
                    "📬 <b>Resultados dos seus palpites e apostas</b>",
                    "✅ «{q}» — {o}", "❌ «{q}» — resultado: {o}", "🎉 «{q}» — +${p}",
                    "Abrir mercado", "Resgatar ganhos", "Mais mercados", "Abrir o app", "Sim", "Não"),
            "zh", new Texts(
                    "✅ <b>你的预测成真了</b>\n「{q}」— {o}",
                    "❌ <b>你的预测没有成真</b>\n「{q}」— 结果：{o}",
                    "🎉 <b>你的投注赢了！</b>\n「{q}」— {o}\n可领取：${p}。请在「钱包」标签页领取。",
                    "准确率：{c}/{t}",
                    "📬 <b>你的预测和投注结果</b>",
                    "✅ 「{q}」— {o}", "❌ 「{q}」— 结果：{o}", "🎉 「{q}」— +${p}",
                    "打开市场", "领取奖金", "更多市场", "打开应用", "是", "否"));

    private NotificationTexts() {}

    static Texts of(String locale) {
        return locale == null ? EN : BY_LOCALE.getOrDefault(locale, EN);
    }
}
