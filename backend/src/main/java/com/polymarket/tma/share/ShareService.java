package com.polymarket.tma.share;

import com.polymarket.tma.auth.repo.AppUserRepository;
import com.polymarket.tma.common.ApiException;
import com.polymarket.tma.market.MarketService;
import com.polymarket.tma.market.dto.MarketDto;
import com.polymarket.tma.market.i18n.MarketTranslationService;
import com.polymarket.tma.referral.ReferralService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Builds a shareable market card: a deep link {@code t.me/<bot>?startapp=m_<id>[_y|_n]} that opens
 * the market in the mini app, and a prepared Telegram message (title, odds, market image, an "Open"
 * button) the user can send to any chat with one tap.
 */
@Service
public class ShareService {

    private static final Logger log = LoggerFactory.getLogger(ShareService.class);

    private final MarketService markets;
    private final MarketTranslationService translations;
    private final TelegramBotClient bot;
    private final AppUserRepository users;
    private final ReferralService referrals;

    public ShareService(MarketService markets, MarketTranslationService translations, TelegramBotClient bot,
                        AppUserRepository users, ReferralService referrals) {
        this.markets = markets;
        this.translations = translations;
        this.bot = bot;
        this.users = users;
        this.referrals = referrals;
    }

    public Mono<ShareDtos.MarketShare> shareMarket(long userId, ShareDtos.MarketShareRequest req, String locale) {
        Long telegramId = users.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("USER_GONE", "User not found"))
                .getTelegramId();
        ShareTexts.Texts texts = ShareTexts.of(locale);
        String refCode = referrals.codeOf(userId);
        return markets.get(req.marketKey())
                .flatMap(m -> translations.translateMarket(m, locale))
                .zipWith(bot.username())
                .flatMap(tuple -> {
                    MarketDto m = tuple.getT1();
                    String link = marketLink(tuple.getT2(), m, req.side(), refCode);
                    String plain = plainText(m, req.side(), texts);
                    return bot.savePreparedInlineMessage(telegramId, card(m, req.side(), texts, link))
                            .map(id -> new ShareDtos.MarketShare(link, plain, id))
                            .onErrorResume(e -> {
                                // Older clients / API hiccups: the app falls back to t.me/share with the link.
                                log.warn("Prepared share message failed for market {}: {}", m.id(), e.toString());
                                return Mono.just(new ShareDtos.MarketShare(link, plain, null));
                            });
                });
    }

    /** Deep link to a market carrying the sharer's pick and invite code (see {@code StartParam}). */
    public static String marketLink(String botUsername, MarketDto m, String side, String refCode) {
        return "https://t.me/" + botUsername + "?startapp=" + startParam(m, side)
                + (refCode == null ? "" : "_r" + refCode);
    }

    /** {@code m_<gamma id>} plus the sharer's pick; Telegram allows [A-Za-z0-9_-]{1,64}. */
    static String startParam(MarketDto m, String side) {
        String id = m.id() != null && m.id().matches("\\d+") ? m.id() : null;
        if (id == null) {
            throw ApiException.badRequest("MARKET_NOT_SHAREABLE", "Market has no Gamma id");
        }
        return "m_" + id + ("yes".equals(side) ? "_y" : "no".equals(side) ? "_n" : "");
    }

    static String oddsLine(MarketDto m, ShareTexts.Texts t) {
        Integer yes = percent(m, "yes", 0);
        Integer no = percent(m, "no", 1);
        if (yes == null && no == null) {
            return "";
        }
        return t.yes() + " " + (yes == null ? "—" : yes + "%") + " · " + t.no() + " " + (no == null ? "—" : no + "%");
    }

    private static Integer percent(MarketDto m, String outcome, int fallbackIndex) {
        List<String> outcomes = m.outcomes() == null ? List.of() : m.outcomes();
        int idx = -1;
        for (int i = 0; i < outcomes.size(); i++) {
            if (outcome.equalsIgnoreCase(outcomes.get(i))) {
                idx = i;
            }
        }
        if (idx < 0) {
            idx = fallbackIndex;
        }
        try {
            return m.outcomePrices() == null || idx >= m.outcomePrices().size()
                    ? null : (int) Math.round(Double.parseDouble(m.outcomePrices().get(idx)) * 100);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static String plainText(MarketDto m, String side, ShareTexts.Texts t) {
        StringBuilder sb = new StringBuilder("📊 ").append(m.question());
        String odds = oddsLine(m, t);
        if (!odds.isEmpty()) {
            sb.append("\n").append(odds);
        }
        if (side != null) {
            sb.append("\n\n").append(String.format(t.myPick(), "yes".equals(side) ? t.yes() : t.no()))
                    .append("\n").append(t.yourTurn());
        }
        return sb.toString();
    }

    /**
     * Escapes text for Telegram's HTML parse mode, which knows only {@code &lt; &gt; &amp; &quot;}
     * (and numeric references): named entities like {@code &middot;} would show up literally.
     */
    public static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Telegram inline result for a market card: odds text, image preview and an "Open market" button. */
    public static Map<String, Object> card(MarketDto m, String side, ShareTexts.Texts t, String link) {
        StringBuilder html = new StringBuilder("📊 <b>").append(escape(m.question())).append("</b>");
        String odds = oddsLine(m, t);
        if (!odds.isEmpty()) {
            html.append("\n").append(escape(odds));
        }
        if (side != null) {
            html.append("\n\n").append(escape(
                    String.format(t.myPick(), "yes".equals(side) ? t.yes() : t.no())))
                    .append("\n").append(escape(t.yourTurn()));
        }
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("message_text", html.toString());
        content.put("parse_mode", "HTML");
        if (m.image() != null && !m.image().isBlank()) {
            // The market picture as a large preview above the text.
            content.put("link_preview_options", Map.of(
                    "url", m.image(), "prefer_large_media", true, "show_above_text", true));
        } else {
            content.put("link_preview_options", Map.of("is_disabled", true));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "article");
        result.put("id", UUID.randomUUID().toString());
        result.put("title", m.question());
        if (!odds.isEmpty()) {
            result.put("description", odds);
        }
        if (m.image() != null && !m.image().isBlank()) {
            result.put("thumbnail_url", m.image());
        }
        result.put("input_message_content", content);
        result.put("reply_markup", Map.of("inline_keyboard",
                List.of(List.of(Map.of("text", "🔮 " + t.open(), "url", link)))));
        return result;
    }
}
