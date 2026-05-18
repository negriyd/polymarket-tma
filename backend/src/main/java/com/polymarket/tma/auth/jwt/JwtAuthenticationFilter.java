package com.polymarket.tma.auth.jwt;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    /**
     * Request attribute set on validation failure. {@code SecurityConfig#authenticationEntryPoint}
     * picks it up and surfaces the reason as an {@code X-Auth-Error} response header so the SPA
     * (and curl) can diagnose without needing to read backend logs.
     */
    public static final String AUTH_ERROR_ATTR = "com.polymarket.tma.auth.error";

    private final JwtService jwt;
    /**
     * In Spring Security 6 {@code SecurityContextHolderFilter} loads the deferred SecurityContext
     * from a {@link SecurityContextRepository} on every dispatch (REQUEST and ASYNC). Mutations via
     * {@code SecurityContextHolder.getContext().setAuthentication(...)} live only in the thread-local
     * for the current dispatch — they are not auto-persisted because the default
     * {@code requireExplicitSave=true}. Controllers that return {@code Mono<>} (e.g. positions,
     * orderbook) get an ASYNC re-dispatch when the publisher completes: SCHF re-loads from the
     * repository and finds nothing, our filter is skipped (OncePerRequestFilter#shouldNotFilterAsyncDispatch
     * defaults to true), and {@code AuthorizationFilter} sees a null authentication → 401.
     *
     * Explicitly saving the context into the request-scoped repository persists it across that
     * boundary so the async re-dispatch reads the same authentication back.
     */
    private final SecurityContextRepository securityContextRepository = new RequestAttributeSecurityContextRepository();

    public JwtAuthenticationFilter(JwtService jwt) {
        this.jwt = jwt;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        // Diagnostic header so DevTools / curl can see whether this filter even ran for a request.
        // Set to one of: "no-header", "already-auth", "ok", "fail". Removed after the L1/positions
        // saga is verified.
        if (header == null || !header.startsWith(BEARER)) {
            res.setHeader("X-Auth-Filter", "no-header");
        } else if (SecurityContextHolder.getContext().getAuthentication() != null) {
            res.setHeader("X-Auth-Filter", "already-auth");
        } else {
            try {
                Claims claims = jwt.parse(header.substring(BEARER.length()));
                if (!"access".equals(claims.get("typ"))) {
                    throw new IllegalArgumentException("Not an access token");
                }
                String username = claims.get("username", String.class);
                AuthPrincipal principal = new AuthPrincipal(jwt.extractUserId(claims), username);
                UsernamePasswordAuthenticationToken authn = new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
                authn.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
                // Use createEmptyContext + setContext + saveContext (rather than mutating the
                // existing context via getContext().setAuthentication) so the repository observes a
                // distinct, fresh context object. This is the recommended SS6 pattern when running
                // with the default requireExplicitSave=true.
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authn);
                SecurityContextHolder.setContext(context);
                securityContextRepository.saveContext(context, req, res);
                res.setHeader("X-Auth-Filter", "ok");
                logger.debug("JWT accepted for " + req.getMethod() + " " + req.getRequestURI()
                        + " (uid=" + principal.userId() + ")");
            } catch (RuntimeException ex) {
                // Unauthenticated requests fall through to Security's default handling. We don't write
                // a 401 here directly because permitAll endpoints (e.g. /api/auth/refresh) still need
                // to succeed even if a stale Authorization header is attached.
                String reason = ex.getClass().getSimpleName() + ": " + ex.getMessage();
                req.setAttribute(AUTH_ERROR_ATTR, reason);
                res.setHeader("X-Auth-Filter", "fail");
                logger.warn("JWT validation failed for " + req.getMethod() + " " + req.getRequestURI() + " — " + reason);
            }
        }
        chain.doFilter(req, res);
    }
}
