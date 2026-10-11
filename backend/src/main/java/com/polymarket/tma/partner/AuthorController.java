package com.polymarket.tma.partner;

import com.polymarket.tma.auth.jwt.AuthPrincipal;
import com.polymarket.tma.common.ApiException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * <pre>
 *   GET /api/authors/current?code=&conditionId= -> {author}: partner whose link was opened (or who
 *                                                  invited me), accuracy, their pick on the market; null if none
 *   GET /api/authors/{code}/leaderboard?week=current|previous -> weekly "Top forecaster" of their audience
 * </pre>
 */
@RestController
public class AuthorController {

    private final AuthorService service;

    public AuthorController(AuthorService service) {
        this.service = service;
    }

    public record Current(AuthorService.Author author) {}

    @GetMapping("/api/authors/current")
    public Current current(@AuthenticationPrincipal AuthPrincipal principal,
                           @RequestParam(required = false) String code,
                           @RequestParam(required = false) String conditionId) {
        return new Current(service.forViewer(userId(principal), code, conditionId).orElse(null));
    }

    @GetMapping("/api/authors/{code}/leaderboard")
    public AuthorService.Leaderboard leaderboard(@AuthenticationPrincipal AuthPrincipal principal,
                                                 @PathVariable String code,
                                                 @RequestParam(defaultValue = "current") String week) {
        return service.leaderboard(code, "previous".equals(week), userId(principal));
    }

    private static long userId(AuthPrincipal principal) {
        if (principal == null) {
            throw ApiException.unauthorized("UNAUTHENTICATED", "Authentication required");
        }
        return principal.userId();
    }
}
