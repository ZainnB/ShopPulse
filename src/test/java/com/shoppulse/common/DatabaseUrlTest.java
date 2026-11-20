package com.shoppulse.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatabaseUrlTest {

    @Test
    @DisplayName("a Neon URI becomes a JDBC URL with sslmode kept and credentials split out")
    void convertsNeonStyleUri() {
        DatabaseUrl.Parsed p = DatabaseUrl.parse(
                "postgresql://shoppulse_owner:npg_secret@ep-cool-bird-123.eu-central-1.aws.neon.tech/shoppulse?sslmode=require");

        assertThat(p.jdbcUrl())
                .isEqualTo("jdbc:postgresql://ep-cool-bird-123.eu-central-1.aws.neon.tech/shoppulse?sslmode=require");
        assertThat(p.username()).isEqualTo("shoppulse_owner");
        assertThat(p.password()).isEqualTo("npg_secret");
    }

    @Test
    @DisplayName("the postgres:// scheme and an explicit port are both handled")
    void handlesPostgresSchemeAndPort() {
        DatabaseUrl.Parsed p = DatabaseUrl.parse("postgres://user:pw@db.example.com:5433/shop");

        assertThat(p.jdbcUrl()).isEqualTo("jdbc:postgresql://db.example.com:5433/shop");
        assertThat(p.username()).isEqualTo("user");
        assertThat(p.password()).isEqualTo("pw");
    }

    @Test
    @DisplayName("a password containing URL-encoded characters is decoded")
    void decodesEncodedPassword() {
        DatabaseUrl.Parsed p = DatabaseUrl.parse("postgresql://user:p%40ss%3Aword@host/db");

        assertThat(p.password()).isEqualTo("p@ss:word");
    }

    @Test
    @DisplayName("an existing JDBC URL is passed through untouched, with no credentials invented")
    void leavesJdbcUrlsAlone() {
        String jdbc = "jdbc:postgresql://localhost:5432/shoppulse";

        assertThat(DatabaseUrl.isUri(jdbc)).isFalse();
        DatabaseUrl.Parsed p = DatabaseUrl.parse(jdbc);
        assertThat(p.jdbcUrl()).isEqualTo(jdbc);
        assertThat(p.username()).isNull();
        assertThat(p.password()).isNull();
    }

    @Test
    @DisplayName("blank input fails loudly and passwords never appear in messages")
    void rejectsBlankAndRedacts() {
        assertThatThrownBy(() -> DatabaseUrl.parse("  "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(DatabaseUrl.redact("postgresql://user:supersecret@host/db"))
                .isEqualTo("postgresql://****@host/db")
                .doesNotContain("supersecret");
        assertThat(DatabaseUrl.isUri(null)).isFalse();
    }
}
