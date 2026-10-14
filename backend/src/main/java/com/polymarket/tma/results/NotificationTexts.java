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

    private static final Map<String, Texts> BY_LOCALE = Map.ofEntries(
            Map.entry("ru", new Texts(
                    "✅ <b>Прогноз сбылся</b>\n«{q}» — {o}",
                    "❌ <b>Прогноз не сбылся</b>\n«{q}» — итог: {o}",
                    "🎉 <b>Ставка выиграла!</b>\n«{q}» — {o}\nК получению: ${p}. Заберите выигрыш на вкладке «Кошелёк».",
                    "Точность: {c} из {t}",
                    "📬 <b>Итоги ваших прогнозов и ставок</b>",
                    "✅ «{q}» — {o}", "❌ «{q}» — итог: {o}", "🎉 «{q}» — +${p}",
                    "Открыть рынок", "Забрать выигрыш", "Другие рынки", "Открыть приложение", "Да", "Нет")),
            Map.entry("uk", new Texts(
                    "✅ <b>Прогноз справдився</b>\n«{q}» — {o}",
                    "❌ <b>Прогноз не справдився</b>\n«{q}» — підсумок: {o}",
                    "🎉 <b>Ставка виграла!</b>\n«{q}» — {o}\nДо отримання: ${p}. Заберіть виграш на вкладці «Гаманець».",
                    "Точність: {c} з {t}",
                    "📬 <b>Підсумки ваших прогнозів і ставок</b>",
                    "✅ «{q}» — {o}", "❌ «{q}» — підсумок: {o}", "🎉 «{q}» — +${p}",
                    "Відкрити ринок", "Забрати виграш", "Інші ринки", "Відкрити застосунок", "Так", "Ні")),
            Map.entry("es", new Texts(
                    "✅ <b>Tu pronóstico se cumplió</b>\n«{q}» — {o}",
                    "❌ <b>Tu pronóstico no se cumplió</b>\n«{q}» — resultado: {o}",
                    "🎉 <b>¡Tu apuesta ganó!</b>\n«{q}» — {o}\nPor cobrar: ${p}. Cóbralo en la pestaña Billetera.",
                    "Precisión: {c} de {t}",
                    "📬 <b>Resultados de tus pronósticos y apuestas</b>",
                    "✅ «{q}» — {o}", "❌ «{q}» — resultado: {o}", "🎉 «{q}» — +${p}",
                    "Abrir mercado", "Cobrar ganancias", "Más mercados", "Abrir la app", "Sí", "No")),
            Map.entry("pt", new Texts(
                    "✅ <b>Seu palpite se confirmou</b>\n«{q}» — {o}",
                    "❌ <b>Seu palpite não se confirmou</b>\n«{q}» — resultado: {o}",
                    "🎉 <b>Sua aposta ganhou!</b>\n«{q}» — {o}\nA receber: ${p}. Resgate na aba Carteira.",
                    "Precisão: {c} de {t}",
                    "📬 <b>Resultados dos seus palpites e apostas</b>",
                    "✅ «{q}» — {o}", "❌ «{q}» — resultado: {o}", "🎉 «{q}» — +${p}",
                    "Abrir mercado", "Resgatar ganhos", "Mais mercados", "Abrir o app", "Sim", "Não")),
            Map.entry("zh", new Texts(
                    "✅ <b>你的预测成真了</b>\n「{q}」— {o}",
                    "❌ <b>你的预测没有成真</b>\n「{q}」— 结果：{o}",
                    "🎉 <b>你的投注赢了！</b>\n「{q}」— {o}\n可领取：${p}。请在「钱包」标签页领取。",
                    "准确率：{c}/{t}",
                    "📬 <b>你的预测和投注结果</b>",
                    "✅ 「{q}」— {o}", "❌ 「{q}」— 结果：{o}", "🎉 「{q}」— +${p}",
                    "打开市场", "领取奖金", "更多市场", "打开应用", "是", "否")),
            Map.entry("de", new Texts(
                    "✅ <b>Deine Prognose ist eingetroffen</b>\n„{q}“ — {o}",
                    "❌ <b>Deine Prognose ist nicht eingetroffen</b>\n„{q}“ — Ergebnis: {o}",
                    "🎉 <b>Deine Wette hat gewonnen!</b>\n„{q}“ — {o}\nAuszahlung: ${p}. Löse sie im Tab Wallet ein.",
                    "Trefferquote: {c} von {t}",
                    "📬 <b>Ergebnisse deiner Prognosen und Wetten</b>",
                    "✅ „{q}“ — {o}", "❌ „{q}“ — Ergebnis: {o}", "🎉 „{q}“ — +${p}",
                    "Markt öffnen", "Gewinn einlösen", "Weitere Märkte", "App öffnen", "Ja", "Nein")),
            Map.entry("fr", new Texts(
                    "✅ <b>Ton pronostic s’est réalisé</b>\n«{q}» — {o}",
                    "❌ <b>Ton pronostic ne s’est pas réalisé</b>\n«{q}» — résultat : {o}",
                    "🎉 <b>Ton pari est gagnant !</b>\n«{q}» — {o}\nÀ encaisser : ${p}. Encaisse-le dans l’onglet Portefeuille.",
                    "Précision : {c} sur {t}",
                    "📬 <b>Résultats de tes pronostics et paris</b>",
                    "✅ «{q}» — {o}", "❌ «{q}» — résultat : {o}", "🎉 «{q}» — +${p}",
                    "Ouvrir le marché", "Encaisser les gains", "Plus de marchés", "Ouvrir l’app", "Oui", "Non")),
            Map.entry("it", new Texts(
                    "✅ <b>La tua previsione si è avverata</b>\n«{q}» — {o}",
                    "❌ <b>La tua previsione non si è avverata</b>\n«{q}» — risultato: {o}",
                    "🎉 <b>La tua scommessa ha vinto!</b>\n«{q}» — {o}\nDa riscuotere: ${p}. Riscuotila nella scheda Portafoglio.",
                    "Precisione: {c} su {t}",
                    "📬 <b>Risultati delle tue previsioni e scommesse</b>",
                    "✅ «{q}» — {o}", "❌ «{q}» — risultato: {o}", "🎉 «{q}» — +${p}",
                    "Apri mercato", "Riscuoti vincite", "Altri mercati", "Apri l’app", "Sì", "No")),
            Map.entry("pl", new Texts(
                    "✅ <b>Twoja prognoza się sprawdziła</b>\n„{q}” — {o}",
                    "❌ <b>Twoja prognoza się nie sprawdziła</b>\n„{q}” — wynik: {o}",
                    "🎉 <b>Twój zakład wygrał!</b>\n„{q}” — {o}\nDo odebrania: ${p}. Odbierz na karcie Portfel.",
                    "Trafność: {c} z {t}",
                    "📬 <b>Wyniki twoich prognoz i zakładów</b>",
                    "✅ „{q}” — {o}", "❌ „{q}” — wynik: {o}", "🎉 „{q}” — +${p}",
                    "Otwórz rynek", "Odbierz wygraną", "Więcej rynków", "Otwórz aplikację", "Tak", "Nie")),
            Map.entry("id", new Texts(
                    "✅ <b>Prediksimu terbukti</b>\n«{q}» — {o}",
                    "❌ <b>Prediksimu tidak terbukti</b>\n«{q}» — hasil: {o}",
                    "🎉 <b>Taruhanmu menang!</b>\n«{q}» — {o}\nUntuk diambil: ${p}. Klaim di tab Dompet.",
                    "Akurasi: {c} dari {t}",
                    "📬 <b>Hasil prediksi dan taruhanmu</b>",
                    "✅ «{q}» — {o}", "❌ «{q}» — hasil: {o}", "🎉 «{q}» — +${p}",
                    "Buka pasar", "Klaim kemenangan", "Pasar lainnya", "Buka aplikasi", "Ya", "Tidak")),
            Map.entry("tl", new Texts(
                    "✅ <b>Tumama ang hula mo</b>\n«{q}» — {o}",
                    "❌ <b>Hindi tumama ang hula mo</b>\n«{q}» — resulta: {o}",
                    "🎉 <b>Nanalo ang taya mo!</b>\n«{q}» — {o}\nMakukuha: ${p}. I-claim ito sa tab na Wallet.",
                    "Accuracy: {c} sa {t}",
                    "📬 <b>Mga resulta ng iyong mga hula at taya</b>",
                    "✅ «{q}» — {o}", "❌ «{q}» — resulta: {o}", "🎉 «{q}» — +${p}",
                    "Buksan ang market", "I-claim ang panalo", "Iba pang market", "Buksan ang app", "Oo", "Hindi")),
            Map.entry("vi", new Texts(
                    "✅ <b>Dự đoán của bạn đã đúng</b>\n«{q}» — {o}",
                    "❌ <b>Dự đoán của bạn chưa đúng</b>\n«{q}» — kết quả: {o}",
                    "🎉 <b>Bạn đã cược thắng!</b>\n«{q}» — {o}\nCó thể nhận: ${p}. Hãy nhận ở tab Ví.",
                    "Độ chính xác: {c}/{t}",
                    "📬 <b>Kết quả dự đoán và cược của bạn</b>",
                    "✅ «{q}» — {o}", "❌ «{q}» — kết quả: {o}", "🎉 «{q}» — +${p}",
                    "Mở thị trường", "Nhận tiền thắng", "Thêm thị trường", "Mở ứng dụng", "Có", "Không")),
            Map.entry("hi", new Texts(
                    "✅ <b>आपका अनुमान सही निकला</b>\n«{q}» — {o}",
                    "❌ <b>आपका अनुमान सही नहीं निकला</b>\n«{q}» — नतीजा: {o}",
                    "🎉 <b>आपका दांव जीत गया!</b>\n«{q}» — {o}\nपाने के लिए: ${p}. इसे वॉलेट टैब पर क्लेम करें।",
                    "सटीकता: {t} में से {c}",
                    "📬 <b>आपके अनुमानों और दांवों के नतीजे</b>",
                    "✅ «{q}» — {o}", "❌ «{q}» — नतीजा: {o}", "🎉 «{q}» — +${p}",
                    "मार्केट खोलें", "जीत क्लेम करें", "और मार्केट", "ऐप खोलें", "हाँ", "नहीं")),
            Map.entry("bn", new Texts(
                    "✅ <b>আপনার পূর্বাভাস সত্যি হয়েছে</b>\n«{q}» — {o}",
                    "❌ <b>আপনার পূর্বাভাস সত্যি হয়নি</b>\n«{q}» — ফলাফল: {o}",
                    "🎉 <b>আপনার বাজি জিতেছে!</b>\n«{q}» — {o}\nপ্রাপ্য: ${p}। ওয়ালেট ট্যাবে সংগ্রহ করুন।",
                    "নির্ভুলতা: {t}-এর মধ্যে {c}",
                    "📬 <b>আপনার পূর্বাভাস ও বাজির ফলাফল</b>",
                    "✅ «{q}» — {o}", "❌ «{q}» — ফলাফল: {o}", "🎉 «{q}» — +${p}",
                    "মার্কেট খুলুন", "জয়ের অর্থ সংগ্রহ করুন", "আরও মার্কেট", "অ্যাপ খুলুন", "হ্যাঁ", "না")),
            Map.entry("th", new Texts(
                    "✅ <b>การคาดการณ์ของคุณถูกต้อง</b>\n«{q}» — {o}",
                    "❌ <b>การคาดการณ์ของคุณไม่ถูกต้อง</b>\n«{q}» — ผลลัพธ์: {o}",
                    "🎉 <b>เดิมพันของคุณชนะ!</b>\n«{q}» — {o}\nรอรับ: ${p} รับเงินได้ที่แท็บกระเป๋าเงิน",
                    "ความแม่นยำ: {c} จาก {t}",
                    "📬 <b>ผลการคาดการณ์และการเดิมพันของคุณ</b>",
                    "✅ «{q}» — {o}", "❌ «{q}» — ผลลัพธ์: {o}", "🎉 «{q}» — +${p}",
                    "เปิดตลาด", "รับเงินรางวัล", "ตลาดอื่น ๆ", "เปิดแอป", "ใช่", "ไม่")),
            Map.entry("ja", new Texts(
                    "✅ <b>予想が的中しました</b>\n「{q}」— {o}",
                    "❌ <b>予想は外れました</b>\n「{q}」— 結果：{o}",
                    "🎉 <b>ベットに勝ちました！</b>\n「{q}」— {o}\n受取額：${p}。「ウォレット」タブで受け取ってください。",
                    "的中率：{t} 件中 {c} 件",
                    "📬 <b>予想とベットの結果</b>",
                    "✅ 「{q}」— {o}", "❌ 「{q}」— 結果：{o}", "🎉 「{q}」— +${p}",
                    "マーケットを開く", "配当を受け取る", "他のマーケット", "アプリを開く", "はい", "いいえ")));

    private NotificationTexts() {}

    static Texts of(String locale) {
        return locale == null ? EN : BY_LOCALE.getOrDefault(locale, EN);
    }
}
