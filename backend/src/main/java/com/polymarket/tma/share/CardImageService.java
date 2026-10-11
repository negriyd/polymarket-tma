package com.polymarket.tma.share;

import com.polymarket.tma.auth.repo.AppUserRepository;
import com.polymarket.tma.market.MarketService;
import com.polymarket.tma.market.dto.MarketDto;
import com.polymarket.tma.market.dto.PriceHistoryDto;
import com.polymarket.tma.market.i18n.MarketTranslationService;
import com.polymarket.tma.partner.AuthorService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * PNG market cards for posts and stories ({@link CardImageRenderer}), with the price history of the
 * first outcome and, for a partner's card ({@code ref} = their invite code), their public accuracy.
 * Rendered cards are kept in memory for a few minutes.
 */
@Service
public class CardImageService {

    private static final Logger log = LoggerFactory.getLogger(CardImageService.class);
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final int MAX_ENTRIES = 200;
    private static final long MAX_ICON_BYTES = 3_000_000;

    private record Entry(byte[] png, long expiresAt) {}

    private final Map<String, Entry> cache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    private final MarketService markets;
    private final MarketTranslationService translations;
    private final TelegramBotClient bot;
    private final AppUserRepository users;
    private final AuthorService authors;
    private final WebClient http;

    public CardImageService(MarketService markets, MarketTranslationService translations, TelegramBotClient bot,
                            AppUserRepository users, AuthorService authors, WebClient.Builder builder) {
        this.markets = markets;
        this.translations = translations;
        this.bot = bot;
        this.users = users;
        this.authors = authors;
        this.http = builder.build();
    }

    public Mono<byte[]> card(String marketKey, String locale, CardImageRenderer.Size size, String ref) {
        String key = marketKey + "|" + locale + "|" + size + "|" + (ref == null ? "" : ref);
        synchronized (cache) {
            Entry e = cache.get(key);
            if (e != null && e.expiresAt() > System.currentTimeMillis()) {
                return Mono.just(e.png());
            }
        }
        ShareTexts.Texts texts = ShareTexts.of(locale);
        String authorLine = ref == null || ref.isBlank() ? null
                : users.findByRefCode(ref.trim().toLowerCase())
                        .flatMap(u -> authors.publicAccuracy(u.getId()))
                        .map(a -> ShareTexts.authorLine(texts, a))
                        .orElse(null);
        return markets.get(marketKey)
                .flatMap(m -> translations.translateMarket(m, locale))
                .flatMap(m -> Mono.zip(history(m), icon(m), bot.username().defaultIfEmpty("")).map(t -> {
                    String link = t.getT3().isEmpty() ? "t.me" : "t.me/" + t.getT3();
                    return new CardImageRenderer.Card(m.question(), labels(m, texts), prices(m), t.getT1(),
                            t.getT2().orElse(null), texts.chance(), texts.tagline(), link, authorLine);
                }))
                .publishOn(Schedulers.boundedElastic())
                .map(c -> CardImageRenderer.render(c, size))
                .doOnNext(png -> {
                    synchronized (cache) {
                        cache.put(key, new Entry(png, System.currentTimeMillis() + TTL.toMillis()));
                    }
                });
    }

    /** Binary markets get localized Yes/No; others keep their outcome names (team names etc.). */
    private static List<String> labels(MarketDto m, ShareTexts.Texts t) {
        List<String> out = new ArrayList<>();
        for (String o : m.outcomes() == null ? List.<String>of() : m.outcomes()) {
            out.add("yes".equalsIgnoreCase(o) ? t.yes() : "no".equalsIgnoreCase(o) ? t.no() : o);
        }
        return out;
    }

    private static List<Double> prices(MarketDto m) {
        List<Double> out = new ArrayList<>();
        for (String p : m.outcomePrices() == null ? List.<String>of() : m.outcomePrices()) {
            try {
                out.add(Double.parseDouble(p));
            } catch (NumberFormatException e) {
                out.add(Double.NaN);
            }
        }
        return out;
    }

    /** A week of the first outcome's price; the whole history for younger markets. */
    private Mono<List<double[]>> history(MarketDto m) {
        if (m.clobTokenIds() == null || m.clobTokenIds().isEmpty()) {
            return Mono.just(List.of());
        }
        String token = m.clobTokenIds().get(0);
        return markets.history(token, "1w")
                .flatMap(h -> h.history() != null && h.history().size() >= 2 ? Mono.just(h) : markets.history(token, "max"))
                .map(CardImageService::points)
                .onErrorResume(e -> {
                    log.warn("Card history failed for market {}: {}", m.id(), e.toString());
                    return Mono.just(List.of());
                })
                .defaultIfEmpty(List.of());
    }

    private static List<double[]> points(PriceHistoryDto h) {
        List<double[]> out = new ArrayList<>();
        for (PriceHistoryDto.Point p : h.history() == null ? List.<PriceHistoryDto.Point>of() : h.history()) {
            if (p.t() != null && p.p() != null) {
                out.add(new double[] {p.t(), p.p().doubleValue()});
            }
        }
        return out;
    }

    private Mono<java.util.Optional<BufferedImage>> icon(MarketDto m) {
        String url = m.icon() != null && !m.icon().isBlank() ? m.icon() : m.image();
        if (url == null || url.isBlank() || !url.startsWith("https://")) {
            return Mono.just(java.util.Optional.empty());
        }
        return http.get().uri(url).retrieve().bodyToMono(byte[].class)
                .timeout(Duration.ofSeconds(4))
                .map(bytes -> {
                    try {
                        // ImageIO reads PNG / JPEG / GIF; WebP and others come back null → no icon.
                        return bytes.length > MAX_ICON_BYTES ? java.util.Optional.<BufferedImage>empty()
                                : java.util.Optional.ofNullable(ImageIO.read(new ByteArrayInputStream(bytes)));
                    } catch (Exception e) {
                        return java.util.Optional.<BufferedImage>empty();
                    }
                })
                .onErrorResume(e -> Mono.just(java.util.Optional.empty()))
                .defaultIfEmpty(java.util.Optional.empty());
    }
}
