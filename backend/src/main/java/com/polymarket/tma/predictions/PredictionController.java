package com.polymarket.tma.predictions;

import com.polymarket.tma.auth.jwt.AuthPrincipal;
import com.polymarket.tma.common.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * <pre>
 *   POST /api/predictions            {marketKey, side}  -> make or change a free prediction
 *   GET  /api/predictions[?conditionId=]               -> my predictions
 *   GET  /api/me/track-record                           -> correct / wrong / pending, bets won / lost
 *   POST /api/me/notifications       {enabled}          -> after WebApp.requestWriteAccess
 * </pre>
 */
@RestController
public class PredictionController {

    private final PredictionService service;

    public PredictionController(PredictionService service) {
        this.service = service;
    }

    public record PredictRequest(@NotBlank String marketKey, @NotNull @Pattern(regexp = "yes|no") String side) {}

    public record NotificationsRequest(@NotNull Boolean enabled) {}

    @PostMapping("/api/predictions")
    public PredictionService.Prediction predict(@AuthenticationPrincipal AuthPrincipal principal,
                                                @Valid @RequestBody PredictRequest req) {
        return service.predict(userId(principal), req.marketKey(), req.side());
    }

    @GetMapping("/api/predictions")
    public List<PredictionService.Prediction> list(@AuthenticationPrincipal AuthPrincipal principal,
                                                   @RequestParam(required = false) String conditionId) {
        return service.list(userId(principal), conditionId);
    }

    @GetMapping("/api/me/track-record")
    public PredictionService.TrackRecord trackRecord(@AuthenticationPrincipal AuthPrincipal principal) {
        return service.trackRecord(userId(principal));
    }

    @PostMapping("/api/me/notifications")
    public PredictionService.TrackRecord notifications(@AuthenticationPrincipal AuthPrincipal principal,
                                                       @Valid @RequestBody NotificationsRequest req) {
        long uid = userId(principal);
        service.setAllowsMessages(uid, req.enabled());
        return service.trackRecord(uid);
    }

    private static long userId(AuthPrincipal principal) {
        if (principal == null) {
            throw ApiException.unauthorized("UNAUTHENTICATED", "Authentication required");
        }
        return principal.userId();
    }
}
