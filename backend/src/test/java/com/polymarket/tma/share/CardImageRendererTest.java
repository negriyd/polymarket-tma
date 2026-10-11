package com.polymarket.tma.share;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class CardImageRendererTest {

    private static CardImageRenderer.Card card(String question, String authorLine) {
        List<double[]> history = new ArrayList<>();
        double p = 0.55;
        for (int i = 0; i < 120; i++) {
            p = Math.max(0.05, Math.min(0.95, p + Math.sin(i / 7.0) * 0.012 + (i % 11 == 0 ? 0.03 : 0)));
            history.add(new double[] {1_700_000_000 + i * 3600, p});
        }
        BufferedImage icon = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        var ig = icon.createGraphics();
        ig.setColor(new java.awt.Color(0x1D4ED8));
        ig.fillRect(0, 0, 64, 64);
        ig.dispose();
        return new CardImageRenderer.Card(question, List.of("Да", "Нет"), List.of(0.72, 0.28), history, icon,
                "шанс", "Polymarket в Telegram", "t.me/Polymarket_trader_nds_bot", authorLine);
    }

    @Test
    void rendersSquareAndStoryPngs() throws Exception {
        var c = card("Выиграет ли «Эвертон» у «Ливерпуля» в матче 26 октября? Очень длинный вопрос, чтобы проверить перенос строк",
                "🎯 Точность прогнозов автора: 72% (18 из 25)");
        for (CardImageRenderer.Size size : CardImageRenderer.Size.values()) {
            byte[] png = CardImageRenderer.render(c, size);
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
            assertThat(img.getWidth()).isEqualTo(size.width);
            assertThat(img.getHeight()).isEqualTo(size.height);
            String out = System.getenv("CARD_OUT");
            if (out != null) {
                Files.write(Path.of(out, "card-" + size.name().toLowerCase() + ".png"), png);
            }
        }
    }

    @Test
    void cardWithoutHistoryOrAuthorStillRenders() {
        var c = new CardImageRenderer.Card("比特币会在年底前突破15万美元吗？", List.of("是", "否"), List.of(0.3, 0.7),
                List.of(), null, "概率", "Telegram 上的 Polymarket", "t.me/bot", null);
        assertThat(CardImageRenderer.render(c, CardImageRenderer.Size.SQUARE)).hasSizeGreaterThan(1000);
    }

    @Test
    void stripsEmojiForTheImage() {
        assertThat(CardImageRenderer.stripEmoji("🎯 Точность: 72%")).isEqualTo("Точность: 72%");
    }
}
