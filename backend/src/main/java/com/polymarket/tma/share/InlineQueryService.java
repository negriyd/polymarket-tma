package com.polymarket.tma.share;

import com.fasterxml.jackson.databind.JsonNode;
import com.polymarket.tma.auth.repo.AppUserRepository;
import com.polymarket.tma.market.MarketService;
import com.polymarket.tma.market.dto.MarketDto;
import com.polymarket.tma.market.i18n.MarketLocale;
import com.polymarket.tma.market.i18n.MarketTranslationService;
import com.polymarket.tma.partner.AuthorService;
import com.polymarket.tma.referral.ReferralService;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Inline mode: {@code @bot <words>} in any chat lists matching markets (trending when empty) as
 * cards with odds and an "Open market" button. Links carry the asking user's invite code.
 */
@Service
public class InlineQueryService {

    private static final Logger log = LoggerFactory.getLogger(InlineQueryService.class);
    private static final int RESULTS = 10;

    private final MarketService markets;
    private final MarketTranslationService translations;
    private final TelegramBotClient bot;
    private final AppUserRepository users;
    private final ReferralService referrals;
    private final AuthorService authors;

    public InlineQueryService(MarketService markets, MarketTranslationService translations, TelegramBotClient bot,
                              AppUserRepository users, ReferralService referrals, AuthorService authors) {
        this.authors = authors;
        this.markets = markets;
        this.translations = translations;
        this.bot = bot;
        this.users = users;
        this.referrals = referrals;
    }

    public Mono<Void> answer(JsonNode inlineQuery) {
        String queryId = inlineQuery.path("id").asText();
        String text = inlineQuery.path("query").asText("").trim();
        long telegramId = inlineQuery.path("from").path("id").asLong();
        String locale = MarketLocale.fromHeader(inlineQuery.path("from").path("language_code").asText(null));
        var user = users.findByTelegramId(telegramId);
        String refCode = user.map(u -> referrals.codeOf(u.getId())).orElse(null);
        ShareTexts.Texts texts = ShareTexts.of(locale);
        String authorLine = ShareTexts.authorLine(texts,
                user.flatMap(u -> authors.publicAccuracy(u.getId())).orElse(null));

        return markets.list(0, RESULTS, "volume24hr", false, null, text.isEmpty() ? null : text)
                .flatMap(resp -> translations.translateMarkets(resp.items(), locale))
                .zipWith(bot.username())
                .map(tuple -> tuple.getT1().stream()
                        .filter(m -> m.id() != null && m.id().matches("\\d+"))
                        .map(m -> ShareService.card(m, null, texts,
                                ShareService.marketLink(tuple.getT2(), m, null, refCode), authorLine))
                        .toList())
                .flatMap(results -> bot.answerInlineQuery(queryId, results))
                .doOnError(e -> log.warn("Inline query '{}' failed: {}", text, e.toString()))
                .onErrorResume(e -> Mono.empty());
    }
}
