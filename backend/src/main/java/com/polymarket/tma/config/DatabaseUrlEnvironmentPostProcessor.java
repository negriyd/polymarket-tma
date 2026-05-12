package com.polymarket.tma.config;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Fly.io / Heroku expose {@code DATABASE_URL} as {@code postgres://user:pass@host:port/db?params}.
 * This app reads {@code POSTGRES_URL} (JDBC). If {@code POSTGRES_URL} is absent, map from
 * {@code DATABASE_URL} so {@code fly postgres attach} works without manual JDBC conversion.
 */
public final class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String PROPERTY_SOURCE_NAME = "databaseUrlToPostgresMapping";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String existing = environment.getProperty("POSTGRES_URL");
        if (existing != null && !existing.isBlank()) {
            return;
        }
        String databaseUrl = firstNonBlank(environment.getProperty("DATABASE_URL"), System.getenv("DATABASE_URL"));
        if (databaseUrl == null) {
            return;
        }
        databaseUrl = databaseUrl.trim();
        Parsed parsed = parsePostgresDatabaseUrl(databaseUrl);
        if (parsed == null) {
            return;
        }
        Map<String, Object> map = new HashMap<>();
        map.put("POSTGRES_URL", parsed.jdbcUrl());
        map.put("POSTGRES_USER", parsed.user());
        map.put("POSTGRES_PASSWORD", parsed.password());
        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, map));
    }

    static Parsed parsePostgresDatabaseUrl(String url) {
        if (url.startsWith("jdbc:")) {
            return null;
        }
        if (!url.startsWith("postgres://") && !url.startsWith("postgresql://")) {
            return null;
        }
        String rest = url.replaceFirst("^postgresql://", "").replaceFirst("^postgres://", "");
        int pathSlash = rest.indexOf('/');
        if (pathSlash < 0) {
            return null;
        }
        String userInfoHostPort = rest.substring(0, pathSlash);
        String dbAndQuery = rest.substring(pathSlash + 1);
        if (dbAndQuery.isEmpty()) {
            return null;
        }
        int at = userInfoHostPort.lastIndexOf('@');
        if (at < 0) {
            return null;
        }
        String userInfo = userInfoHostPort.substring(0, at);
        String hostPort = userInfoHostPort.substring(at + 1);
        int colon = userInfo.indexOf(':');
        if (colon < 0) {
            return null;
        }
        String user = urlDecode(userInfo.substring(0, colon));
        String password = urlDecode(userInfo.substring(colon + 1));

        String host;
        int port = 5432;
        if (hostPort.startsWith("[")) {
            int close = hostPort.indexOf(']');
            if (close < 0) {
                return null;
            }
            host = hostPort.substring(1, close);
            if (hostPort.length() > close + 1 && hostPort.charAt(close + 1) == ':') {
                port = Integer.parseInt(hostPort.substring(close + 2));
            }
        } else if (hostPort.contains(":")) {
            int hc = hostPort.lastIndexOf(':');
            host = hostPort.substring(0, hc);
            port = Integer.parseInt(hostPort.substring(hc + 1));
        } else {
            host = hostPort;
        }

        String dbName;
        String query;
        int q = dbAndQuery.indexOf('?');
        if (q >= 0) {
            dbName = dbAndQuery.substring(0, q);
            query = dbAndQuery.substring(q);
        } else {
            dbName = dbAndQuery;
            query = "";
        }
        String jdbc =
                "jdbc:postgresql://" + host + ":" + port + "/" + dbName
                        + (query.isEmpty() ? "" : query);
        return new Parsed(jdbc, user, password);
    }

    private static String urlDecode(String raw) {
        return URLDecoder.decode(raw, StandardCharsets.UTF_8);
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }

    record Parsed(String jdbcUrl, String user, String password) {}

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
