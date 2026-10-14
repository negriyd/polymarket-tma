package com.polymarket.tma.groups;

import java.util.Map;

/**
 * Bot messages in group chats and the private /start reply, per app locale (English fallback).
 * {name} member name, {i} place, {c}/{t} correct of resolved predictions.
 */
final class GroupTexts {

    record Texts(String welcome, String joinButton, String topTitle, String topLine, String topEmpty,
                 String joined, String needApp, String openApp, String weeklyTitle, String startPrivate) {}

    private static final Texts EN = new Texts(
            "🔮 <b>Forecasting leaderboard for this chat</b>\n"
                    + "Tap the button to join, then make free predictions on Polymarket markets — no money needed. "
                    + "/top shows who called the most this week; every Monday I post the winners.",
            "Join and predict",
            "🏆 <b>Top forecasters of this chat · 7 days</b>",
            "{i}. {name} — {c} of {t}",
            "No resolved predictions this week yet. Tap the button and make yours — the market decides who was right.",
            "✅ {name} joined the leaderboard",
            "{name}, open the app once to join — then send /join again.",
            "Open app",
            "🏆 <b>Last week's top forecasters</b>",
            "🔮 Forecast the news with Polymarket right in Telegram: free predictions, real odds, real bets.\n"
                    + "Add me to a group chat to run a forecasting leaderboard with friends.");

    private static final Map<String, Texts> BY_LOCALE = Map.ofEntries(
            Map.entry("ru", new Texts(
                    "🔮 <b>Рейтинг прогнозистов этого чата</b>\n"
                    + "Нажмите кнопку, чтобы присоединиться, и делайте прогнозы без денег на рынках Polymarket. "
                    + "/top покажет, кто угадал больше всех за неделю; каждый понедельник я публикую победителей.",
                    "Участвовать и прогнозировать",
                    "🏆 <b>Лучшие прогнозисты чата · 7 дней</b>",
                    "{i}. {name} — {c} из {t}",
                    "На этой неделе прогнозов с итогом пока нет. Нажмите кнопку и сделайте свой — рынок покажет, кто был прав.",
                    "✅ {name} в рейтинге",
                    "{name}, откройте приложение хотя бы раз, чтобы участвовать, — затем снова отправьте /join.",
                    "Открыть приложение",
                    "🏆 <b>Лучшие прогнозисты прошлой недели</b>",
                    "🔮 Прогнозируйте новости с Polymarket прямо в Telegram: прогнозы без денег, реальные шансы, реальные ставки.\n"
                    + "Добавьте меня в групповой чат, чтобы устроить рейтинг прогнозистов с друзьями.")),
            Map.entry("uk", new Texts(
                    "🔮 <b>Рейтинг прогнозистів цього чату</b>\n"
                    + "Натисніть кнопку, щоб приєднатися, і робіть прогнози без грошей на ринках Polymarket. "
                    + "/top покаже, хто вгадав найбільше за тиждень; щопонеділка я публікую переможців.",
                    "Долучитися й прогнозувати",
                    "🏆 <b>Найкращі прогнозисти чату · 7 днів</b>",
                    "{i}. {name} — {c} з {t}",
                    "Цього тижня прогнозів з результатом ще немає. Натисніть кнопку й зробіть свій — ринок покаже, хто мав рацію.",
                    "✅ {name} у рейтингу",
                    "{name}, відкрийте застосунок хоча б раз, щоб долучитися, — потім знову надішліть /join.",
                    "Відкрити застосунок",
                    "🏆 <b>Найкращі прогнозисти минулого тижня</b>",
                    "🔮 Прогнозуйте новини з Polymarket просто в Telegram: прогнози без грошей, реальні шанси, реальні ставки.\n"
                    + "Додайте мене в груповий чат, щоб влаштувати рейтинг прогнозистів із друзями.")),
            Map.entry("es", new Texts(
                    "🔮 <b>Ranking de pronósticos de este chat</b>\n"
                    + "Toca el botón para unirte y haz pronósticos gratis en mercados de Polymarket, sin dinero. "
                    + "/top muestra quién acertó más esta semana; cada lunes publico a los ganadores.",
                    "Unirme y pronosticar",
                    "🏆 <b>Mejores pronosticadores del chat · 7 días</b>",
                    "{i}. {name} — {c} de {t}",
                    "Aún no hay pronósticos resueltos esta semana. Toca el botón y haz el tuyo: el mercado decidirá quién acertó.",
                    "✅ {name} se unió al ranking",
                    "{name}, abre la app una vez para unirte y luego envía /join de nuevo.",
                    "Abrir la app",
                    "🏆 <b>Mejores pronosticadores de la semana pasada</b>",
                    "🔮 Pronostica las noticias con Polymarket desde Telegram: pronósticos gratis, probabilidades reales, apuestas reales.\n"
                    + "Agrégame a un chat de grupo para crear un ranking de pronósticos con tus amigos.")),
            Map.entry("pt", new Texts(
                    "🔮 <b>Ranking de palpites deste chat</b>\n"
                    + "Toque no botão para entrar e dê palpites grátis em mercados da Polymarket — sem dinheiro. "
                    + "/top mostra quem mais acertou na semana; toda segunda-feira eu publico os vencedores.",
                    "Entrar e palpitar",
                    "🏆 <b>Melhores palpiteiros do chat · 7 dias</b>",
                    "{i}. {name} — {c} de {t}",
                    "Ainda não há palpites resolvidos nesta semana. Toque no botão e dê o seu — o mercado decide quem acertou.",
                    "✅ {name} entrou no ranking",
                    "{name}, abra o app uma vez para entrar — depois envie /join de novo.",
                    "Abrir o app",
                    "🏆 <b>Melhores palpiteiros da semana passada</b>",
                    "🔮 Preveja as notícias com a Polymarket direto no Telegram: palpites grátis, probabilidades reais, apostas reais.\n"
                    + "Adicione-me a um grupo para criar um ranking de palpites com os amigos.")),
            Map.entry("zh", new Texts(
                    "🔮 <b>本群预测排行榜</b>\n"
                    + "点击按钮加入，然后在 Polymarket 市场上免费预测——无需花钱。"
                    + "/top 查看本周谁猜得最准；每周一我会公布获胜者。",
                    "加入并预测",
                    "🏆 <b>本群预测达人 · 7 天</b>",
                    "{i}. {name} — {c}/{t}",
                    "本周还没有已结算的预测。点击按钮做出你的预测——由市场来判定谁对。",
                    "✅ {name} 已加入排行榜",
                    "{name}，请先打开一次应用以加入，然后再发送 /join。",
                    "打开应用",
                    "🏆 <b>上周预测达人</b>",
                    "🔮 在 Telegram 里用 Polymarket 预测新闻：免费预测、真实赔率、真实投注。\n"
                    + "把我加入群聊，和朋友一起玩预测排行榜。")),
            Map.entry("de", new Texts(
                    "🔮 <b>Prognose-Rangliste für diesen Chat</b>\n"
                    + "Tippe auf den Button, um mitzumachen, und gib kostenlose Prognosen zu Polymarket-Märkten ab – ganz ohne Geld. "
                    + "/top zeigt, wer diese Woche am häufigsten richtig lag; jeden Montag poste ich die Gewinner.",
                    "Mitmachen und tippen",
                    "🏆 <b>Top-Prognostiker dieses Chats · 7 Tage</b>",
                    "{i}. {name} — {c} von {t}",
                    "Diese Woche gibt es noch keine entschiedenen Prognosen. Tippe auf den Button und gib deine ab – der Markt entscheidet, wer richtig lag.",
                    "✅ {name} ist der Rangliste beigetreten",
                    "{name}, öffne einmal die App, um mitzumachen – und sende dann erneut /join.",
                    "App öffnen",
                    "🏆 <b>Top-Prognostiker der letzten Woche</b>",
                    "🔮 Prognostiziere die Nachrichten mit Polymarket direkt in Telegram: kostenlose Prognosen, echte Quoten, echte Wetten.\n"
                    + "Füge mich zu einem Gruppenchat hinzu, um mit Freunden eine Prognose-Rangliste zu starten.")),
            Map.entry("fr", new Texts(
                    "🔮 <b>Classement des pronostics de ce chat</b>\n"
                    + "Touche le bouton pour participer, puis fais des pronostics gratuits sur les marchés Polymarket, sans argent. "
                    + "/top montre qui a vu juste le plus souvent cette semaine ; chaque lundi, je publie les gagnants.",
                    "Participer et pronostiquer",
                    "🏆 <b>Meilleurs pronostiqueurs de ce chat · 7 jours</b>",
                    "{i}. {name} — {c} sur {t}",
                    "Aucun pronostic résolu cette semaine pour l’instant. Touche le bouton et fais le tien : le marché décidera qui avait raison.",
                    "✅ {name} a rejoint le classement",
                    "{name}, ouvre l’app une fois pour participer, puis renvoie /join.",
                    "Ouvrir l’app",
                    "🏆 <b>Meilleurs pronostiqueurs de la semaine dernière</b>",
                    "🔮 Pronostique l’actualité avec Polymarket directement dans Telegram : pronostics gratuits, vraies cotes, vrais paris.\n"
                    + "Ajoute-moi à un chat de groupe pour lancer un classement de pronostics entre amis.")),
            Map.entry("it", new Texts(
                    "🔮 <b>Classifica delle previsioni di questa chat</b>\n"
                    + "Tocca il pulsante per partecipare, poi fai previsioni gratuite sui mercati di Polymarket: niente soldi. "
                    + "/top mostra chi ha indovinato di più questa settimana; ogni lunedì pubblico i vincitori.",
                    "Partecipa e prevedi",
                    "🏆 <b>Migliori previsori della chat · 7 giorni</b>",
                    "{i}. {name} — {c} su {t}",
                    "Ancora nessuna previsione risolta questa settimana. Tocca il pulsante e fai la tua: sarà il mercato a decidere chi aveva ragione.",
                    "✅ {name} è entrato in classifica",
                    "{name}, apri l’app una volta per partecipare, poi invia di nuovo /join.",
                    "Apri l’app",
                    "🏆 <b>Migliori previsori della settimana scorsa</b>",
                    "🔮 Prevedi le notizie con Polymarket direttamente in Telegram: previsioni gratuite, quote reali, scommesse reali.\n"
                    + "Aggiungimi a una chat di gruppo per creare una classifica delle previsioni con gli amici.")),
            Map.entry("pl", new Texts(
                    "🔮 <b>Ranking prognoz tego czatu</b>\n"
                    + "Kliknij przycisk, aby dołączyć, a potem rób darmowe prognozy na rynkach Polymarket — bez pieniędzy. "
                    + "/top pokazuje, kto trafił najwięcej w tym tygodniu; w każdy poniedziałek ogłaszam zwycięzców.",
                    "Dołącz i prognozuj",
                    "🏆 <b>Najlepsi progności czatu · 7 dni</b>",
                    "{i}. {name} — {c} z {t}",
                    "W tym tygodniu nie ma jeszcze rozstrzygniętych prognoz. Kliknij przycisk i zrób swoją — rynek zdecyduje, kto miał rację.",
                    "✅ {name} dołącza do rankingu",
                    "{name}, otwórz raz aplikację, aby dołączyć — potem wyślij /join ponownie.",
                    "Otwórz aplikację",
                    "🏆 <b>Najlepsi progności zeszłego tygodnia</b>",
                    "🔮 Przewiduj wydarzenia z Polymarket prosto w Telegramie: darmowe prognozy, prawdziwe kursy, prawdziwe zakłady.\n"
                    + "Dodaj mnie do czatu grupowego, aby prowadzić ranking prognoz ze znajomymi.")),
            Map.entry("id", new Texts(
                    "🔮 <b>Papan peringkat prediksi untuk chat ini</b>\n"
                    + "Ketuk tombol untuk bergabung, lalu buat prediksi gratis di pasar Polymarket — tanpa uang. "
                    + "/top menampilkan siapa yang paling banyak benar minggu ini; tiap Senin aku umumkan pemenangnya.",
                    "Gabung dan prediksi",
                    "🏆 <b>Peramal terbaik di chat ini · 7 hari</b>",
                    "{i}. {name} — {c} dari {t}",
                    "Belum ada prediksi yang selesai minggu ini. Ketuk tombol dan buat prediksimu — pasar yang menentukan siapa yang benar.",
                    "✅ {name} bergabung ke papan peringkat",
                    "{name}, buka aplikasinya sekali untuk bergabung — lalu kirim /join lagi.",
                    "Buka aplikasi",
                    "🏆 <b>Peramal terbaik minggu lalu</b>",
                    "🔮 Prediksi berita bersama Polymarket langsung di Telegram: prediksi gratis, peluang nyata, taruhan sungguhan.\n"
                    + "Tambahkan aku ke grup untuk membuat papan peringkat prediksi bersama teman.")),
            Map.entry("tl", new Texts(
                    "🔮 <b>Leaderboard ng mga hula para sa chat na ito</b>\n"
                    + "I-tap ang button para sumali, tapos gumawa ng libreng hula sa mga market ng Polymarket — walang perang kailangan. "
                    + "Ipinapakita ng /top kung sino ang pinakamaraming tumama ngayong linggo; tuwing Lunes, ipo-post ko ang mga nanalo.",
                    "Sumali at manghula",
                    "🏆 <b>Mga nangungunang manghuhula sa chat na ito · 7 araw</b>",
                    "{i}. {name} — {c} sa {t}",
                    "Wala pang na-resolve na hula ngayong linggo. I-tap ang button at gumawa ng hula mo — ang market ang magpapasya kung sino ang tama.",
                    "✅ Sumali si {name} sa leaderboard",
                    "{name}, buksan muna ang app nang isang beses para makasali — tapos ipadala ulit ang /join.",
                    "Buksan ang app",
                    "🏆 <b>Mga nangungunang manghuhula noong nakaraang linggo</b>",
                    "🔮 Hulaan ang balita gamit ang Polymarket mismo sa Telegram: libreng hula, totoong odds, totoong taya.\n"
                    + "Idagdag ako sa isang group chat para magpatakbo ng leaderboard ng mga hula kasama ang mga kaibigan.")),
            Map.entry("vi", new Texts(
                    "🔮 <b>Bảng xếp hạng dự đoán của nhóm này</b>\n"
                    + "Nhấn nút để tham gia, rồi dự đoán miễn phí trên các thị trường Polymarket — không cần tiền. "
                    + "/top cho biết ai đoán đúng nhiều nhất tuần này; thứ Hai hằng tuần tôi sẽ công bố người thắng.",
                    "Tham gia và dự đoán",
                    "🏆 <b>Top dự đoán của nhóm · 7 ngày</b>",
                    "{i}. {name} — {c}/{t}",
                    "Tuần này chưa có dự đoán nào có kết quả. Nhấn nút và dự đoán ngay — thị trường sẽ quyết định ai đúng.",
                    "✅ {name} đã tham gia bảng xếp hạng",
                    "{name}, hãy mở ứng dụng một lần để tham gia — rồi gửi lại /join.",
                    "Mở ứng dụng",
                    "🏆 <b>Top dự đoán tuần trước</b>",
                    "🔮 Dự đoán tin tức với Polymarket ngay trong Telegram: dự đoán miễn phí, tỷ lệ thật, cược thật.\n"
                    + "Thêm tôi vào nhóm chat để tổ chức bảng xếp hạng dự đoán với bạn bè.")),
            Map.entry("hi", new Texts(
                    "🔮 <b>इस चैट का पूर्वानुमान लीडरबोर्ड</b>\n"
                    + "जुड़ने के लिए बटन दबाएँ, फिर Polymarket मार्केट पर मुफ़्त अनुमान लगाएँ — पैसे की ज़रूरत नहीं। "
                    + "/top दिखाता है कि इस हफ़्ते सबसे ज़्यादा सही अनुमान किसके रहे; हर सोमवार मैं विजेताओं की सूची पोस्ट करता हूँ।",
                    "जुड़ें और अनुमान लगाएँ",
                    "🏆 <b>इस चैट के टॉप पूर्वानुमानकर्ता · 7 दिन</b>",
                    "{i}. {name} — {t} में से {c}",
                    "इस हफ़्ते अभी किसी अनुमान का नतीजा नहीं आया। बटन दबाएँ और अपना अनुमान लगाएँ — कौन सही था, यह मार्केट तय करेगा।",
                    "✅ {name} लीडरबोर्ड में शामिल हुए",
                    "{name}, जुड़ने के लिए एक बार ऐप खोलें — फिर से /join भेजें।",
                    "ऐप खोलें",
                    "🏆 <b>पिछले हफ़्ते के टॉप पूर्वानुमानकर्ता</b>",
                    "🔮 Telegram में ही Polymarket के साथ खबरों का पूर्वानुमान लगाएँ: मुफ़्त अनुमान, असली ऑड्स, असली दांव।\n"
                    + "दोस्तों के साथ पूर्वानुमान लीडरबोर्ड चलाने के लिए मुझे किसी ग्रुप चैट में जोड़ें।")),
            Map.entry("bn", new Texts(
                    "🔮 <b>এই চ্যাটের পূর্বাভাস লিডারবোর্ড</b>\n"
                    + "যোগ দিতে বোতামে চাপুন, তারপর Polymarket মার্কেটে বিনামূল্যে পূর্বাভাস দিন — টাকা লাগবে না। "
                    + "/top দেখায় এই সপ্তাহে কে সবচেয়ে বেশি সঠিক বলেছে; প্রতি সোমবার আমি বিজয়ীদের পোস্ট করি।",
                    "যোগ দিন ও পূর্বাভাস দিন",
                    "🏆 <b>এই চ্যাটের সেরা পূর্বাভাসদাতা · ৭ দিন</b>",
                    "{i}. {name} — {t}টির মধ্যে {c}টি",
                    "এই সপ্তাহে এখনও কোনো পূর্বাভাস নিষ্পন্ন হয়নি। বোতামে চাপুন এবং আপনারটি দিন — কে ঠিক ছিল, তা মার্কেটই ঠিক করবে।",
                    "✅ {name} লিডারবোর্ডে যোগ দিয়েছেন",
                    "{name}, যোগ দিতে একবার অ্যাপটি খুলুন — তারপর আবার /join পাঠান।",
                    "অ্যাপ খুলুন",
                    "🏆 <b>গত সপ্তাহের সেরা পূর্বাভাসদাতা</b>",
                    "🔮 Telegram-এই Polymarket-এ খবরের পূর্বাভাস দিন: বিনামূল্যে পূর্বাভাস, আসল সম্ভাবনা, আসল বাজি।\n"
                    + "বন্ধুদের সাথে পূর্বাভাস লিডারবোর্ড চালাতে আমাকে কোনো গ্রুপ চ্যাটে যোগ করুন।")),
            Map.entry("th", new Texts(
                    "🔮 <b>ตารางอันดับนักคาดการณ์ของแชตนี้</b>\n"
                    + "แตะปุ่มเพื่อเข้าร่วม แล้วคาดการณ์ฟรีในตลาด Polymarket — ไม่ต้องใช้เงิน "
                    + "/top แสดงว่าใครทายถูกมากที่สุดในสัปดาห์นี้ และทุกวันจันทร์ฉันจะประกาศผู้ชนะ",
                    "เข้าร่วมและคาดการณ์",
                    "🏆 <b>นักคาดการณ์อันดับต้นของแชตนี้ · 7 วัน</b>",
                    "{i}. {name} — {c} จาก {t}",
                    "สัปดาห์นี้ยังไม่มีการคาดการณ์ที่รู้ผล แตะปุ่มแล้วคาดการณ์เลย — ตลาดจะตัดสินว่าใครถูก",
                    "✅ {name} เข้าร่วมตารางอันดับแล้ว",
                    "{name} เปิดแอปหนึ่งครั้งเพื่อเข้าร่วม — แล้วส่ง /join อีกครั้ง",
                    "เปิดแอป",
                    "🏆 <b>นักคาดการณ์อันดับต้นของสัปดาห์ที่แล้ว</b>",
                    "🔮 คาดการณ์ข่าวกับ Polymarket ได้ใน Telegram: คาดการณ์ฟรี อัตราต่อรองจริง เดิมพันจริง\n"
                    + "เพิ่มฉันเข้ากลุ่มแชตเพื่อจัดตารางอันดับนักคาดการณ์กับเพื่อน ๆ")),
            Map.entry("ja", new Texts(
                    "🔮 <b>このチャットの予想ランキング</b>\n"
                    + "ボタンを押して参加し、Polymarket のマーケットで無料予想をしよう。お金は不要です。"
                    + "/top で今週いちばん当てた人を表示。毎週月曜日に上位者を発表します。",
                    "参加して予想する",
                    "🏆 <b>このチャットの予想上位者 · 7日間</b>",
                    "{i}. {name} — {t} 件中 {c} 件",
                    "今週確定した予想はまだありません。ボタンを押して予想しよう。正解はマーケットが決めます。",
                    "✅ {name} さんがランキングに参加しました",
                    "{name} さん、参加するには一度アプリを開いてから、もう一度 /join を送ってください。",
                    "アプリを開く",
                    "🏆 <b>先週の予想上位者</b>",
                    "🔮 Telegram で Polymarket を使ってニュースを予想：無料予想、リアルなオッズ、実際のベット。\n"
                    + "グループチャットに追加すると、友達と予想ランキングができます。")));

    private GroupTexts() {}

    static Texts of(String locale) {
        return locale == null ? EN : BY_LOCALE.getOrDefault(locale, EN);
    }
}
