package com.polymarket.tma.trading;

import com.polymarket.tma.auth.jwt.AuthPrincipal;
import com.polymarket.tma.common.ApiException;
import com.polymarket.tma.trading.dto.OrderDtos;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/orders")
public class TradingController {

    private final TradingService trading;

    public TradingController(TradingService trading) {
        this.trading = trading;
    }

    @PostMapping("/prepare")
    public OrderDtos.TypedDataResponse prepare(@AuthenticationPrincipal AuthPrincipal principal,
                                               @Valid @RequestBody OrderDtos.PrepareOrderRequest req) {
        requireAuth(principal);
        return trading.prepare(principal.userId(), req);
    }

    @PostMapping("/submit")
    public Mono<OrderDtos.SubmittedOrderResponse> submit(@AuthenticationPrincipal AuthPrincipal principal,
                                                         @Valid @RequestBody OrderDtos.SubmitOrderRequest req) {
        requireAuth(principal);
        return trading.submit(principal.userId(), req);
    }

    private static void requireAuth(AuthPrincipal principal) {
        if (principal == null) {
            throw ApiException.unauthorized("UNAUTHENTICATED", "Authentication required");
        }
    }
}
