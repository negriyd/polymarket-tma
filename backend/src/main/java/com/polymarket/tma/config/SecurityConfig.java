package com.polymarket.tma.config;

import com.polymarket.tma.auth.jwt.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final AppProperties props;
    private final JwtAuthenticationFilter jwtFilter;

    public SecurityConfig(AppProperties props, JwtAuthenticationFilter jwtFilter) {
        this.props = props;
        this.jwtFilter = jwtFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // Without an explicit entry point Spring Security maps "no/invalid auth" to 403, which our axios
        // refresh interceptor would never catch. Returning 401 keeps the standard semantics (401 = bad
        // token, refresh; 403 = authenticated but forbidden) and lets the SPA refresh transparently.
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            // JwtAuthenticationFilter records the underlying reason (SignatureException,
                            // ExpiredJwtException, IncorrectClaimException, …) before letting the
                            // request fall through. We echo it as a response header so the SPA / curl
                            // can see exactly why a freshly issued-looking token was rejected without
                            // needing access to backend logs.
                            Object reason = request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTR);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            if (reason != null) {
                                String safe = String.valueOf(reason).replaceAll("[\\r\\n]", " ");
                                response.setHeader("X-Auth-Error", safe);
                            }
                            response.getWriter().write(
                                    "{\"code\":\"UNAUTHENTICATED\",\"message\":\"Authentication required\"}");
                        }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/api/auth/telegram",
                                "/api/auth/refresh",
                                "/api/markets/**",
                                "/api/health",
                                "/actuator/health/**",
                                "/actuator/prometheus",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/ws/**"
                        ).permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Spring Boot auto-registers every {@link jakarta.servlet.Filter} bean (including the
     * {@code @Component}-annotated {@link JwtAuthenticationFilter}) as a top-level servlet filter.
     * That makes our filter run BEFORE Spring Security's {@code FilterChainProxy}; the chain's
     * {@code SecurityContextHolderFilter} then overwrites the thread-local context via
     * {@code setDeferredContext(...)}, dropping the authentication we just set. Our filter is also
     * added via {@code http.addFilterBefore(...)} inside the chain, but
     * {@link org.springframework.web.filter.OncePerRequestFilter} short-circuits the second
     * invocation because the {@code ALREADY_FILTERED} attribute is already present from the outer
     * run — so {@code AuthorizationFilter} sees no auth and the entry point returns 401. Disabling
     * the auto-registration here keeps the filter exclusively inside the security chain, where the
     * SecurityContext it sets actually survives until authorization runs.
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setEnabled(false);
        return reg;
    }

    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowCredentials(true);
        // Patterns (not strict origins) so dev tunnels like https://*.trycloudflare.com match.
        cfg.setAllowedOriginPatterns(props.cors().allowedOrigins());
        cfg.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(Arrays.asList("*"));
        cfg.setExposedHeaders(Arrays.asList("Authorization", "X-Trace-Id", "X-Auth-Error", "X-Auth-Filter"));
        source.registerCorsConfiguration("/**", cfg);
        return new CorsFilter(source);
    }
}
