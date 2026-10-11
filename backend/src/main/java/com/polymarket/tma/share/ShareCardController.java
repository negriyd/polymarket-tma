package com.polymarket.tma.share;

import com.polymarket.tma.market.i18n.MarketLocale;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * {@code GET /api/markets/{marketKey}/card.png?lang=ru&size=square|story&ref=<invite code>} — public
 * (under {@code /api/markets/**}) so Telegram stories and social networks can fetch it by URL.
 */
@RestController
public class ShareCardController {

    private final CardImageService cards;

    public ShareCardController(CardImageService cards) {
        this.cards = cards;
    }

    @GetMapping(value = "/api/markets/{marketKey}/card.png", produces = MediaType.IMAGE_PNG_VALUE)
    public Mono<ResponseEntity<byte[]>> card(@PathVariable String marketKey,
                                             @RequestParam(required = false) String lang,
                                             @RequestParam(defaultValue = "square") String size,
                                             @RequestParam(required = false) String ref) {
        CardImageRenderer.Size s = "story".equals(size) ? CardImageRenderer.Size.STORY : CardImageRenderer.Size.SQUARE;
        String safeRef = ref != null && ref.matches("[a-z0-9]{4,16}") ? ref : null;
        return cards.card(marketKey, MarketLocale.fromHeader(lang), s, safeRef)
                .map(png -> ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_PNG)
                        .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePublic())
                        .body(png));
    }
}
