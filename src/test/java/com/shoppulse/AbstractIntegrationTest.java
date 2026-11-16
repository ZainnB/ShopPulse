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
        // Tests seed their own fixtures; the demo generator stays off.
        registry.add("shoppulse.seed.enabled", () -> false);
        registry.add("spring.ai.anthropic.api-key", () -> "not-set");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
}
