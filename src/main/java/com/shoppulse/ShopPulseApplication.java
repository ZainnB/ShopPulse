package com.shoppulse;

import com.shoppulse.common.DatabaseUrl;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableCaching
@EnableScheduling
public class ShopPulseApplication {

    public static void main(String[] args) {
        normaliseDatabaseUrl();
        SpringApplication.run(ShopPulseApplication.class, args);
    }

    /**
     * Lets {@code DATABASE_URL} hold either a JDBC URL or the {@code postgresql://user:pass@host/db}
     * URI that hosted providers (Neon, Supabase, Railway, Heroku) hand out. Run before the context
     * starts so the converted values are in place when the datasource is built. A JDBC URL, or no
     * URL at all, is left untouched and the defaults in application.yml apply.
     */
    private static void normaliseDatabaseUrl() {
        String url = System.getenv("DATABASE_URL");
        if (!DatabaseUrl.isUri(url)) {
            return;
        }
        DatabaseUrl.Parsed parsed = DatabaseUrl.parse(url);
        System.setProperty("spring.datasource.url", parsed.jdbcUrl());
        if (parsed.username() != null) {
            System.setProperty("spring.datasource.username", parsed.username());
        }
        if (parsed.password() != null) {
            System.setProperty("spring.datasource.password", parsed.password());
        }
        System.out.println("Converted DATABASE_URL to JDBC form: " + DatabaseUrl.redact(parsed.jdbcUrl()));
    }
}
