package com.polymarket.tma.share;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public final class ShareDtos {

    private ShareDtos() {}

    /** @param side the sharer's pick ("yes" / "no") for a "what do you think?" challenge; optional */
    public record MarketShareRequest(@NotBlank String marketKey, @Pattern(regexp = "yes|no") String side) {}

    /**
     * @param link              t.me deep link that opens the market in the mini app
     * @param text              plain text for share sheets outside Telegram's prepared messages
     * @param preparedMessageId for {@code WebApp.shareMessage}; null when Telegram couldn't prepare it
     */
    public record MarketShare(String link, String text, String preparedMessageId) {}
}
