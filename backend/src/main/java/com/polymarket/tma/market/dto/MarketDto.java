package com.polymarket.tma.market.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MarketDto(
        /** Gamma internal market id (stringified); required for {@code GET /markets/{id}} on Gamma. */
        @JsonProperty("id") String id,
        @JsonProperty("conditionId") String conditionId,
        String question,
        String slug,
        String description,
        String category,
        String image,
        String icon,
        @JsonProperty("endDate")
        @JsonDeserialize(using = PolymarketInstantDeserializer.class)
        Instant endDate,
        @JsonProperty("gameStartTime")
        @JsonDeserialize(using = PolymarketInstantDeserializer.class)
        Instant gameStartTime,
        @JsonProperty("marketSlug") String marketSlug,
        Boolean active,
        Boolean closed,
        @JsonProperty("acceptingOrders") Boolean acceptingOrders,
        Boolean archived,
        BigDecimal volume,
        @JsonProperty("volume24hr") BigDecimal volume24h,
        BigDecimal liquidity,
        @JsonDeserialize(using = PolymarketStringListDeserializer.class)
        List<String> outcomes,
        @JsonProperty("clobTokenIds")
        @JsonDeserialize(using = PolymarketStringListDeserializer.class)
        List<String> clobTokenIds,
        @JsonProperty("outcomePrices")
        @JsonDeserialize(using = PolymarketStringListDeserializer.class)
        List<String> outcomePrices,
        /**
         * Polymarket markets live on one of two on-chain CTF Exchange instances and orders MUST
         * be signed against the matching {@code verifyingContract}. {@code negRisk == true} means
         * the order has to use the NegRisk CTF Exchange domain; {@code false}/{@code null}
         * means the regular CTF Exchange. Submitting against the wrong one returns
         * {@code 400 "order_version_mismatch"}.
         */
        @JsonProperty("negRisk") Boolean negRisk,
        /** CLOB price grid (0.01 or 0.001); an off-grid limit price is rejected by CLOB. */
        @JsonProperty("orderPriceMinTickSize") BigDecimal orderPriceMinTickSize,
        /** Smallest order CLOB accepts, in outcome shares (typically 5). */
        @JsonProperty("orderMinSize") BigDecimal orderMinSize,
        /** Label of this market inside its multi-market event ("Oct 31", "Gavin Newsom"); translated by Gamma. */
        @JsonProperty("groupItemTitle") String groupItemTitle
) {
    /** Copy with a translated {@code question}. */
    public MarketDto withQuestion(String translated) {
        return new MarketDto(id, conditionId, translated, slug, description, category, image, icon, endDate,
                gameStartTime, marketSlug, active, closed, acceptingOrders, archived, volume, volume24h, liquidity,
                outcomes, clobTokenIds, outcomePrices, negRisk, orderPriceMinTickSize, orderMinSize, groupItemTitle);
    }

    /** Market without CLOB order constraints (tests, cache entries written before they were mapped). */
    public MarketDto(String id, String conditionId, String question, String slug, String description,
                     String category, String image, String icon, Instant endDate, Instant gameStartTime,
                     String marketSlug, Boolean active, Boolean closed, Boolean acceptingOrders,
                     Boolean archived, BigDecimal volume, BigDecimal volume24h, BigDecimal liquidity,
                     List<String> outcomes, List<String> clobTokenIds, List<String> outcomePrices,
                     Boolean negRisk) {
        this(id, conditionId, question, slug, description, category, image, icon, endDate, gameStartTime,
                marketSlug, active, closed, acceptingOrders, archived, volume, volume24h, liquidity,
                outcomes, clobTokenIds, outcomePrices, negRisk, null, null, null);
    }
}
