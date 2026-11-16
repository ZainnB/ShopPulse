package com.shoppulse;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for integration tests: a real PostgreSQL 16 via Testcontainers, with Flyway
 * running the production migrations, so the views and constraints under test are the ones
 * that ship. The container is static, so every test class reuses one database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("shoppulse")
            .withUsername("shoppulse")
            .withPassword("shoppulse")
            .withReuse(false);

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        // Tests run against the real src/main/resources/application.yml — there is deliberately
        // no test copy of it, because a second application.yml on the test classpath replaces
        // the real one wholesale and silently hides properties the app needs.
        // Only the handful of values that must differ for a test are overridden here.
        registry.add("shoppulse.seed.enabled", () -> false);          // tests seed their own fixtures
        registry.add("spring.ai.anthropic.api-key", () -> "not-set"); // exercise the fallback path
        registry.add("shoppulse.jwt.secret", () -> "test-secret-key-for-shoppulse-integration-tests-32b");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.thymeleaf.cache", () -> false);
    }
}
