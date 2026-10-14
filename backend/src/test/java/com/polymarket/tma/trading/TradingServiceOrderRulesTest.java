package com.polymarket.tma.trading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.polymarket.tma.common.ApiException;
import com.polymarket.tma.market.dto.MarketDto;
import com.polymarket.tma.trading.dto.OrderDtos;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;

class TradingServiceOrderRulesTest {

    private static OrderDtos.PrepareOrderRequest req(String price, String size) {
        return new OrderDtos.PrepareOrderRequest("0xcid", "1", OrderDtos.Side.BUY, new BigDecimal(price),
                new BigDecimal(size), null, OrderDtos.SignatureType.POLY_1271, null, null);
    }

    private static MarketDto market(String tick, String minSize) {
        return new MarketDto("0", "0xcid", "Q?", null, null, null, null, null, null, null, null,
                true, false, true, false, null, null, null, List.of("Yes", "No"), List.of("1", "2"),
                List.of("0.5", "0.5"), true,
                tick == null ? null : new BigDecimal(tick), minSize == null ? null : new BigDecimal(minSize), null);
    }

    @Test
    void cutsSizeToCentsSoAmountsDivideBackToThePrice() {
        // The order CLOB rejected on 2026-10-11: $10 at 0.53 -> 18.867924... shares.
        var fitted = TradingService.fitToOrderRules(req("0.53", "18.867924"), market("0.01", "5"));
        assertThat(fitted.size()).isEqualByComparingTo("18.86");

        BigInteger maker = fitted.size().multiply(fitted.price()).movePointRight(6).toBigIntegerExact();
        BigInteger taker = fitted.size().movePointRight(6).toBigIntegerExact();
        assertThat(new BigDecimal(maker).divide(new BigDecimal(taker))).isEqualByComparingTo("0.53");
    }

    @Test
    void rejectsPriceOffTheTickGrid() {
        assertThatThrownBy(() -> TradingService.fitToOrderRules(req("0.535", "10"), market("0.01", "5")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("steps of 0.01");
        assertThat(TradingService.fitToOrderRules(req("0.535", "10"), market("0.001", "5")).price())
                .isEqualByComparingTo("0.535");
    }

    @Test
    void defaultsToCentTickWhenMarketIsUnknown() {
        assertThatThrownBy(() -> TradingService.fitToOrderRules(req("0.535", "10"), null))
                .isInstanceOf(ApiException.class);
        assertThat(TradingService.fitToOrderRules(req("0.53", "1.869"), null).size()).isEqualByComparingTo("1.86");
    }

    @Test
    void rejectsOrdersBelowMarketMinimum() {
        assertThatThrownBy(() -> TradingService.fitToOrderRules(req("0.53", "1.869159"), market("0.01", "5")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Minimum order is 5 shares (≈ $2.65");
    }

    private static OrderDtos.PrepareOrderRequest fak(String cap, String amount) {
        return new OrderDtos.PrepareOrderRequest("0xcid", "1", OrderDtos.Side.BUY, new BigDecimal(cap),
                BigDecimal.ONE, OrderDtos.OrderType.FAK, OrderDtos.SignatureType.POLY_1271, null, null,
                new BigDecimal(amount));
    }

    @Test
    void fakBuySpendsWholeCentsAndSkipsTheShareMinimum() {
        var fitted = TradingService.fitToOrderRules(fak("0.56", "1.009"), market("0.01", "5"));
        assertThat(fitted.amount()).isEqualByComparingTo("1.00");
        assertThat(fitted.size()).isEqualByComparingTo("1.7857");
    }

    @Test
    void fakBuyBelowOneDollarIsRejected() {
        assertThatThrownBy(() -> TradingService.fitToOrderRules(fak("0.56", "0.99"), market("0.01", "5")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Minimum bet is $1");
    }
}
