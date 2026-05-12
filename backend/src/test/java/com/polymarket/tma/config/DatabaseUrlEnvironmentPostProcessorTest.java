package com.polymarket.tma.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DatabaseUrlEnvironmentPostProcessorTest {

    @Test
    void parsesFlyStylePostgresUrl() {
        var p = DatabaseUrlEnvironmentPostProcessor.parsePostgresDatabaseUrl(
                "postgres://myuser:mypass%3Ax@top2.nearest.of.mydb.internal:5432/myapp?sslmode=disable");
        assertThat(p).isNotNull();
        assertThat(p.user()).isEqualTo("myuser");
        assertThat(p.password()).isEqualTo("mypass:x");
        assertThat(p.jdbcUrl())
                .isEqualTo("jdbc:postgresql://top2.nearest.of.mydb.internal:5432/myapp?sslmode=disable");
    }

    @Test
    void parsesUrlWithoutExplicitPort() {
        var p = DatabaseUrlEnvironmentPostProcessor.parsePostgresDatabaseUrl(
                "postgres://u:p@db.example.com/mydb");
        assertThat(p).isNotNull();
        assertThat(p.jdbcUrl()).isEqualTo("jdbc:postgresql://db.example.com:5432/mydb");
    }

    @Test
    void ignoresJdbcUrl() {
        assertThat(DatabaseUrlEnvironmentPostProcessor.parsePostgresDatabaseUrl(
                        "jdbc:postgresql://localhost:5432/x"))
                .isNull();
    }

    @Test
    void ignoresWhenPostgresNotPresent() {
        assertThat(DatabaseUrlEnvironmentPostProcessor.parsePostgresDatabaseUrl("mysql://a:b@c/d")).isNull();
    }
}
