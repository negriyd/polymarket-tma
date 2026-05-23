package com.polymarket.tma.fees;

import com.polymarket.tma.config.AppProperties;
import com.polymarket.tma.trading.dto.OrderDtos;
import com.polymarket.tma.wallet.ApprovalDtos;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;

/**
 * Computes the platform fee for a prepared order and turns it into an ERC-20 USDC
 * {@code transfer(recipient, amount)} unsigned transaction.
 *
 * <p>The fee is applied to the order's <strong>USDC notional</strong>, defined as
 * {@code price * size}. For a buy order the notional is exactly the USDC the user spends; for a
 * sell order it is the USDC the user expects to receive once filled. The fee is always charged in
 * USDC on Polygon and is independent of CLOB matching: it lands as a regular ERC-20 transfer right
 * before the order goes to CLOB.
 *
 * <p>Returns {@code null} when the fee feature is disabled (zero rate or no recipient configured),
 * or when the computed fee rounds down to {@code 0} micro-USDC (notional &lt; {@code 10000/bps}).
 */
@Service
public class FeeService {

    /** USDC has 6 decimals: 1 USDC = 1_000_000 micro-USDC. */
    private static final BigInteger USDC_DECIMALS_FACTOR = BigInteger.valueOf(1_000_000L);

    /** Basis-point denominator: 10_000 bps = 100%. */
    private static final BigDecimal BPS_DIVISOR = new BigDecimal(10_000);

    private static final int POLYGON_CHAIN_ID = 137;

    private final AppProperties props;

    public FeeService(AppProperties props) {
        this.props = props;
    }

    /**
     * @return a quote describing the fee in micro-USDC + the unsigned tx the wallet must broadcast,
     *     or {@code null} when no fee should be charged.
     */
    public FeeQuote quote(OrderDtos.PrepareOrderRequest req) {
        AppProperties.Fees cfg = props.fees();
        if (cfg == null || !cfg.enabled()) {
            return null;
        }
        if (req.price() == null || req.size() == null) {
            return null;
        }

        BigDecimal notionalUsdc = req.price().multiply(req.size());
        BigDecimal feeUsdc = notionalUsdc
                .multiply(BigDecimal.valueOf(cfg.spreadBps()))
                .divide(BPS_DIVISOR, 6, RoundingMode.HALF_UP);
        BigInteger feeMicroUsdc = feeUsdc.multiply(new BigDecimal(USDC_DECIMALS_FACTOR)).toBigInteger();
        if (feeMicroUsdc.signum() <= 0) {
            return null;
        }

        String calldata = FeeCalldataBuilder.erc20Transfer(cfg.recipientAddress(), feeMicroUsdc);
        ApprovalDtos.UnsignedTx tx = new ApprovalDtos.UnsignedTx(
                "TRADING_FEE_TRANSFER",
                props.polygon().usdcAddress(),
                calldata,
                "0x0",
                POLYGON_CHAIN_ID);

        return new FeeQuote(feeMicroUsdc, cfg.spreadBps(), cfg.recipientAddress(), tx);
    }

    /**
     * @param amountMicroUsdc fee in USDC base units (1 USDC = 1_000_000).
     * @param spreadBps       basis points actually applied (echoes config).
     * @param recipient       USDC recipient address (Polygon).
     * @param tx              unsigned ERC-20 transfer the wallet broadcasts.
     */
    public record FeeQuote(
            BigInteger amountMicroUsdc,
            int spreadBps,
            String recipient,
            ApprovalDtos.UnsignedTx tx
    ) {}
}
