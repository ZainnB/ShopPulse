package com.shoppulse.ai;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Wiring for the AI module: typed properties, the SQL guard sized from them, the optional
 * dedicated read-only connection pool, and scheduling for the weekly briefing.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

    @Bean
    public SqlGuard sqlGuard(AiProperties properties) {
        return new SqlGuard(properties.sql().maxRows());
    }

    /**
     * Created only when {@code shoppulse.ai.sql.readonly-url} is non-blank. It logs in as the
     * {@code shoppulse_ai_ro} role from migration V3, which can read the five views and nothing else.
     * Registered under the name {@code aiReadOnlyDataSource}; see {@link AiReadOnlyDataSource} for
     * why it is not a raw DataSource bean.
     */
    @Bean(name = "aiReadOnlyDataSource")
    @ConditionalOnExpression("'${shoppulse.ai.sql.readonly-url:}'.trim() != ''")
    public AiReadOnlyDataSource aiReadOnlyDataSource(AiProperties properties) {
        AiProperties.Sql sql = properties.sql();
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(sql.readonlyUrl().trim());
        ds.setUsername(sql.readonlyUser());
        ds.setPassword(sql.readonlyPassword());
        ds.setReadOnly(true);
        ds.setMaximumPoolSize(3);
        ds.setPoolName("shoppulse-ai-ro-pool");
        return new AiReadOnlyDataSource(ds);
    }
}
