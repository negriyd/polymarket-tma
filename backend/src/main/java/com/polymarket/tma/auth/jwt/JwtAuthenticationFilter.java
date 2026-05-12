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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final JwtService jwt;

    public JwtAuthenticationFilter(JwtService jwt) {
        this.jwt = jwt;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER) && SecurityContextHolder.getContext().getAuthentication() == null) {
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
                SecurityContextHolder.getContext().setAuthentication(authn);
            } catch (RuntimeException ex) {
                // Unauthenticated requests fall through to Security's default handling.
                logger.debug("JWT validation failed: " + ex.getMessage());
            }
        }
        chain.doFilter(req, res);
    }
}
