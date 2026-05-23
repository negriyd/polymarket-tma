package com.polymarket.tma.fees;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.polymarket.tma.config.AppProperties;
import com.polymarket.tma.trading.dto.OrderDtos;
import java.math.BigDecimal;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeeServiceTest {

    private static final String USDC = "0x2791Bca1f2de4661ED88A30C99A7a9449Aa84174";
    private static final String FEE_WALLET = "0x000000000000000000000000000000000000feed";

    @Mock private AppProperties props;

    @Test
    void returnsNullWhenDisabled() {
        when(props.fees()).thenReturn(new AppProperties.Fees(0, FEE_WALLET));
        FeeService svc = new FeeService(props);
        assertThat(svc.quote(req("0.5", "10"))).isNull();
    }

    @Test
    void returnsNullWhenRecipientMissing() {
        when(props.fees()).thenReturn(new AppProperties.Fees(50, ""));
        FeeService svc = new FeeService(props);
        assertThat(svc.quote(req("0.5", "10"))).isNull();
    }

    @Test
    void zeroPointFivePercentOfFiveDollars() {
        when(props.polygon()).thenReturn(polygon());
        when(props.fees()).thenReturn(new AppProperties.Fees(50, FEE_WALLET));
        FeeService svc = new FeeService(props);

        FeeService.FeeQuote quote = svc.quote(req("0.5", "10")); // notional = 5 USDC

        assertThat(quote).isNotNull();
        assertThat(quote.spreadBps()).isEqualTo(50);
        // 5 USDC * 0.5% = 0.025 USDC = 25_000 micro-USDC.
        assertThat(quote.amountMicroUsdc()).isEqualTo(BigInteger.valueOf(25_000L));
        assertThat(quote.tx().to()).isEqualTo(USDC);
        assertThat(quote.tx().kind()).isEqualTo("TRADING_FEE_TRANSFER");
        assertThat(quote.tx().chainId()).isEqualTo(137);
        // ERC-20 transfer selector.
        assertThat(quote.tx().data()).startsWith("0xa9059cbb");
        assertThat(quote.tx().data().toLowerCase()).contains(FEE_WALLET.substring(2).toLowerCase());
    }

    @Test
    void roundsDownToZeroForTinyNotional() {
        when(props.fees()).thenReturn(new AppProperties.Fees(1, FEE_WALLET)); // 0.01%
        FeeService svc = new FeeService(props);

        // notional = 0.000001 USDC * 0.01% rounds to 0 micro-USDC -> no fee tx.
        FeeService.FeeQuote quote = svc.quote(req("0.5", "0.000002"));
        assertThat(quote).isNull();
    }

    private static OrderDtos.PrepareOrderRequest req(String price, String size) {
        return new OrderDtos.PrepareOrderRequest(
                "0xcid",
                "1",
                OrderDtos.Side.BUY,
                new BigDecimal(price),
                new BigDecimal(size),
                null,
                OrderDtos.SignatureType.EOA,
                null,
                null);
    }

    private static AppProperties.Polygon polygon() {
        return new AppProperties.Polygon("https://polygon-rpc.com", USDC, "0x4bFb41d5B3570DeFd03C39a9A4D8dE6Bd8B8982E", "0xC5d563A36AE78145C45a50134d48A1215220f80a", "0x4D97DCd97eC945f40cF65F87097ACe5EA0476045");
    }
}
