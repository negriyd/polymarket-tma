package com.polymarket.tma.share;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;

/**
 * Draws a market card as PNG for social networks: question, chance of the first outcome with its
 * change, a step chart of the price history, the odds bar and a footer with the bot link.
 * {@link Size#SQUARE} fits X / Instagram posts, {@link Size#STORY} — stories and TikTok.
 */
public final class CardImageRenderer {

    public enum Size {
        SQUARE(1080, 1080), STORY(1080, 1920);

        final int width;
        final int height;

        Size(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }

    /**
     * @param outcomes   outcome labels, first = the one charted (Yes for binary markets)
     * @param prices     current prices 0..1 per outcome
     * @param history    price of the first outcome over time, oldest first (may be empty)
     * @param authorLine a partner's accuracy line, or null
     */
    public record Card(String question, List<String> outcomes, List<Double> prices, List<double[]> history,
                       BufferedImage icon, String chanceWord, String tagline, String botLink, String authorLine) {}

    private static final Color BG_TOP = new Color(0x0B1220);
    private static final Color BG_BOTTOM = new Color(0x15213D);
    private static final Color TEXT = new Color(0xF1F5F9);
    private static final Color HINT = new Color(0x94A3B8);
    private static final Color ACCENT = new Color(0x4F8CFF);
    private static final Color UP = new Color(0x22C55E);
    private static final Color DOWN = new Color(0xEF4444);
    private static final Color GOLD = new Color(0xFBBF24);
    private static final int PAD = 72;

    private static final String FAMILY = pickFamily();

    private CardImageRenderer() {}

    /** A font family that has Latin, Cyrillic and, ideally, CJK glyphs (Noto CJK in the Docker image). */
    private static String pickFamily() {
        Set<String> available = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String f : List.of("Noto Sans CJK SC", "Noto Sans CJK JP", "PingFang SC", "Helvetica Neue",
                "Arial Unicode MS", "DejaVu Sans")) {
            if (available.contains(f)) {
                return f;
            }
        }
        return Font.SANS_SERIF;
    }

    private static Font font(int style, int size, String forText) {
        Font f = new Font(FAMILY, style, size);
        // Fall back to the logical font (fontconfig fallback chain) for glyphs the family lacks.
        return forText == null || f.canDisplayUpTo(forText) == -1 ? f : new Font(Font.SANS_SERIF, style, size);
    }

    public static byte[] render(Card c, Size size) {
        BufferedImage img = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setPaint(new GradientPaint(0, 0, BG_TOP, 0, size.height, BG_BOTTOM));
            g.fillRect(0, 0, size.width, size.height);
            draw(g, c, size);
        } finally {
            g.dispose();
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void draw(Graphics2D g, Card c, Size size) {
        boolean story = size == Size.STORY;
        int w = size.width - 2 * PAD;
        int y = story ? 180 : PAD;

        // Header: market icon and tagline.
        int iconSize = story ? 128 : 104;
        if (c.icon() != null) {
            var clip = new RoundRectangle2D.Double(PAD, y, iconSize, iconSize, 28, 28);
            g.setClip(clip);
            g.drawImage(c.icon(), PAD, y, iconSize, iconSize, null);
            g.setClip(null);
        }
        int textX = c.icon() != null ? PAD + iconSize + 28 : PAD;
        Font tagFont = font(Font.BOLD, story ? 40 : 34, c.tagline());
        g.setFont(tagFont);
        g.setColor(HINT);
        g.drawString(c.tagline(), textX, y + iconSize / 2 + g.getFontMetrics().getAscent() / 2 - 6);
        y += iconSize + (story ? 64 : 44);

        // Question.
        Font qFont = font(Font.BOLD, story ? 68 : 50, c.question());
        g.setFont(qFont);
        g.setColor(TEXT);
        FontMetrics qm = g.getFontMetrics();
        List<String> lines = wrap(c.question(), qm, w, story ? 5 : 3);
        for (String line : lines) {
            y += qm.getAscent();
            g.drawString(line, PAD, y);
            y += qm.getDescent() + 8;
        }
        y += story ? 56 : 32;

        // Chance of the first outcome and its change over the charted period.
        // Current price, consistent with the odds bar; the history only gives the change.
        double now = c.prices().isEmpty() ? Double.NaN : c.prices().get(0);
        if (Double.isNaN(now) && !c.history().isEmpty()) {
            now = c.history().get(c.history().size() - 1)[1];
        }
        String pct = Double.isNaN(now) ? "—" : Math.round(now * 100) + "%";
        Font bigFont = font(Font.BOLD, story ? 176 : 112, pct);
        g.setFont(bigFont);
        g.setColor(ACCENT);
        FontMetrics bm = g.getFontMetrics();
        y += bm.getAscent() - (story ? 24 : 18);
        g.drawString(pct, PAD, y);
        int afterPct = PAD + bm.stringWidth(pct) + 28;
        String label = c.chanceWord() + " · " + (c.outcomes().isEmpty() ? "" : c.outcomes().get(0));
        g.setFont(font(Font.PLAIN, story ? 50 : 38, label));
        g.setColor(HINT);
        g.drawString(ellipsize(label, g.getFontMetrics(), size.width - PAD - afterPct), afterPct, y - (story ? 70 : 46));
        if (c.history().size() >= 2 && !Double.isNaN(now)) {
            long change = Math.round((now - c.history().get(0)[1]) * 100);
            if (change != 0) {
                String ch = (change > 0 ? "▲ " : "▼ ") + Math.abs(change) + "%";
                g.setFont(font(Font.BOLD, story ? 46 : 34, ch));
                g.setColor(change > 0 ? UP : DOWN);
                g.drawString(ch, afterPct, y);
            }
        }
        y += story ? 64 : 40;

        // Footer block, laid out from the bottom up.
        int bottom = size.height - (story ? 180 : PAD);
        Font footFont = font(Font.PLAIN, story ? 40 : 32, c.botLink());
        g.setFont(footFont);
        FontMetrics fm = g.getFontMetrics();
        g.setColor(HINT);
        g.drawString(c.botLink(), PAD, bottom);
        int footTop = bottom - fm.getAscent();
        if (c.authorLine() != null) {
            String author = stripEmoji(c.authorLine());
            g.setFont(font(Font.BOLD, story ? 42 : 34, author));
            g.setColor(GOLD);
            footTop -= 24;
            g.drawString(ellipsize(author, g.getFontMetrics(), w), PAD, footTop);
            footTop -= g.getFontMetrics().getAscent();
        }

        // Odds bar above the footer.
        int barH = story ? 30 : 24;
        int barY = footTop - (story ? 72 : 52) - barH;
        drawOddsBar(g, c, PAD, barY, w, barH, story);
        int labelsTop = barY - (story ? 70 : 58);

        // Chart fills the space between the chance and the odds labels.
        int chartTop = y;
        int chartBottom = labelsTop - (story ? 56 : 36);
        if (c.history().size() >= 2 && chartBottom - chartTop > 100) {
            drawChart(g, c.history(), PAD, chartTop, w, chartBottom - chartTop, story);
        }
    }

    private static void drawOddsBar(Graphics2D g, Card c, int x, int y, int w, int h, boolean story) {
        if (c.prices().size() < 2) {
            return;
        }
        double a = c.prices().get(0);
        double b = c.prices().get(1);
        double sum = a + b;
        if (!(sum > 0)) {
            return;
        }
        int split = (int) Math.round(w * a / sum);
        g.setColor(DOWN);
        g.fill(new RoundRectangle2D.Double(x, y, w, h, h, h));
        g.setColor(UP);
        g.fill(new RoundRectangle2D.Double(x, y, Math.max(h, split), h, h, h));
        if (split > h && split < w - h) {
            g.fillRect(x + split - h, y, h, h);
            g.setColor(DOWN);
            g.fillRect(x + split, y, Math.min(h, w - split), h);
        }

        String left = c.outcomes().get(0) + " " + Math.round(a * 100) + "%";
        String right = c.outcomes().get(1) + " " + Math.round(b * 100) + "%";
        g.setFont(font(Font.BOLD, story ? 46 : 38, left + right));
        FontMetrics m = g.getFontMetrics();
        int ly = y - (story ? 22 : 16);
        g.setColor(UP);
        g.drawString(ellipsize(left, m, w / 2 - 16), x, ly);
        g.setColor(DOWN);
        String r = ellipsize(right, m, w / 2 - 16);
        g.drawString(r, x + w - m.stringWidth(r), ly);
    }

    private static void drawChart(Graphics2D g, List<double[]> pts, int x, int y, int w, int h, boolean story) {
        double t0 = pts.get(0)[0];
        double t1 = pts.get(pts.size() - 1)[0];
        double lo = 1;
        double hi = 0;
        for (double[] p : pts) {
            lo = Math.min(lo, p[1]);
            hi = Math.max(hi, p[1]);
        }
        lo = Math.max(0, lo - 0.04);
        hi = Math.min(1, hi + 0.04);
        int labelW = story ? 96 : 80;
        int cw = w - labelW;
        double fLo = lo;
        double fHi = hi;
        java.util.function.DoubleUnaryOperator px = t -> x + (t1 == t0 ? cw : (t - t0) / (t1 - t0) * cw);
        java.util.function.DoubleUnaryOperator py = p -> y + (fHi == fLo ? h / 2.0 : (1 - (p - fLo) / (fHi - fLo)) * h);

        // Grid at round levels.
        double span = hi - lo;
        double step = span > 0.5 ? 0.25 : span > 0.2 ? 0.1 : span > 0.08 ? 0.05 : 0.02;
        g.setFont(font(Font.PLAIN, story ? 34 : 28, "0%"));
        FontMetrics m = g.getFontMetrics();
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[] {4f, 10f}, 0f));
        for (double v = Math.ceil(lo / step) * step; v <= hi + 1e-9; v += step) {
            int gy = (int) Math.round(py.applyAsDouble(v));
            g.setColor(new Color(148, 163, 184, 60));
            g.drawLine(x, gy, x + cw, gy);
            g.setColor(HINT);
            String s = Math.round(v * 100) + "%";
            g.drawString(s, x + w - m.stringWidth(s), gy + m.getAscent() / 2 - 4);
        }

        // Step line (price holds until the next trade) and a soft area under it.
        Path2D.Double line = new Path2D.Double();
        line.moveTo(px.applyAsDouble(pts.get(0)[0]), py.applyAsDouble(pts.get(0)[1]));
        for (int i = 1; i < pts.size(); i++) {
            line.lineTo(px.applyAsDouble(pts.get(i)[0]), py.applyAsDouble(pts.get(i - 1)[1]));
            line.lineTo(px.applyAsDouble(pts.get(i)[0]), py.applyAsDouble(pts.get(i)[1]));
        }
        Path2D.Double area = new Path2D.Double(line);
        area.lineTo(px.applyAsDouble(t1), y + h);
        area.lineTo(px.applyAsDouble(t0), y + h);
        area.closePath();
        g.setPaint(new GradientPaint(0, y, new Color(79, 140, 255, 90), 0, y + h, new Color(79, 140, 255, 0)));
        g.fill(area);
        g.setStroke(new BasicStroke(story ? 7f : 6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(ACCENT);
        g.draw(line);
        double[] last = pts.get(pts.size() - 1);
        int r = story ? 14 : 12;
        g.fillOval((int) px.applyAsDouble(last[0]) - r, (int) py.applyAsDouble(last[1]) - r, 2 * r, 2 * r);
    }

    /** Word wrap that also breaks long words / CJK text by characters; the last line gets "…". */
    static List<String> wrap(String text, FontMetrics m, int width, int maxLines) {
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            String candidate = cur.isEmpty() ? word : cur + " " + word;
            if (m.stringWidth(candidate) <= width) {
                cur.setLength(0);
                cur.append(candidate);
                continue;
            }
            if (!cur.isEmpty()) {
                lines.add(cur.toString());
                cur.setLength(0);
            }
            // A word wider than the line: split by code points.
            StringBuilder part = new StringBuilder();
            for (int cp : word.codePoints().toArray()) {
                if (m.stringWidth(part.toString() + Character.toString(cp)) > width && !part.isEmpty()) {
                    lines.add(part.toString());
                    part.setLength(0);
                }
                part.appendCodePoint(cp);
            }
            cur.append(part);
        }
        if (!cur.isEmpty()) {
            lines.add(cur.toString());
        }
        if (lines.size() > maxLines) {
            List<String> cut = new ArrayList<>(lines.subList(0, maxLines));
            cut.set(maxLines - 1, ellipsize(cut.get(maxLines - 1) + " " + lines.get(maxLines), m, width, true));
            return cut;
        }
        return lines;
    }

    static String ellipsize(String s, FontMetrics m, int width) {
        return ellipsize(s, m, width, false);
    }

    private static String ellipsize(String s, FontMetrics m, int width, boolean force) {
        if (!force && m.stringWidth(s) <= width) {
            return s;
        }
        String out = s;
        while (!out.isEmpty() && m.stringWidth(out + "…") > width) {
            out = out.substring(0, out.offsetByCodePoints(out.length(), -1));
        }
        return out.stripTrailing() + "…";
    }

    /** Emoji have no glyphs in the card fonts. */
    static String stripEmoji(String s) {
        StringBuilder sb = new StringBuilder();
        s.codePoints().filter(cp -> Character.getType(cp) != Character.OTHER_SYMBOL && cp != 0xFE0F
                && !Character.isSurrogate((char) cp)).forEach(sb::appendCodePoint);
        return sb.toString().trim();
    }
}
