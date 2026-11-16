package com.shoppulse.ai;

import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

/**
 * Holder for the dedicated read-only pool. It is a wrapper rather than a bare
 * {@link DataSource} bean on purpose: a second DataSource bean would make Spring Boot back off
 * from auto-configuring the primary one and silently break JPA and Flyway.
 */
public final class AiReadOnlyDataSource implements AutoCloseable {

    private final HikariDataSource dataSource;

    public AiReadOnlyDataSource(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public DataSource dataSource() {
        return dataSource;
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
