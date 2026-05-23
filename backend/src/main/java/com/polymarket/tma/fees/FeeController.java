package com.polymarket.tma.fees;

import com.polymarket.tma.config.AppProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public read-only endpoint exposing the current platform fee configuration so the frontend can
 * show a preview ("you will be charged 0.5% = $0.025 USDC") before the user signs an order.
 *
 * <p>Authentication is not required — the values are not secret and the same numbers are echoed
 * back on every {@code POST /api/orders/prepare} call.
 */
@RestController
@RequestMapping("/api/fees")
public class FeeController {

    private final AppProperties props;

    public FeeController(AppProperties props) {
        this.props = props;
    }

    @GetMapping
    public FeeConfigResponse current() {
        AppProperties.Fees cfg = props.fees();
        boolean enabled = cfg != null && cfg.enabled();
        return new FeeConfigResponse(
                enabled,
                cfg == null ? 0 : (cfg.spreadBps() == null ? 0 : cfg.spreadBps()),
                enabled ? cfg.recipientAddress() : null);
    }

    public record FeeConfigResponse(boolean enabled, int spreadBps, String recipient) {}
}
