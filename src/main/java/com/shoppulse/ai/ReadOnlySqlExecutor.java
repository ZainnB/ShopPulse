package com.shoppulse.ai;

import com.shoppulse.common.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs statements that have already passed {@link SqlGuard}, with the database enforcing the
 * limits too, so a bug in the guard is not the only thing between a model and the data.
 *
 * <p><b>Fallback behaviour.</b> If {@code shoppulse.ai.sql.readonly-url} is set, queries run on a
 * dedicated pool logging in as {@code shoppulse_ai_ro} (SELECT on the five views only, migration V3).
 * If it is blank the primary DataSource is used instead; the Java-side guard, a read-only
 * transaction, the statement timeout and the row cap still apply, but the database no longer
 * restricts which relations can be read.
 *
 * <p>In both cases the connection is switched to read-only with auto-commit off (so the driver
 * issues {@code BEGIN READ ONLY}), a query timeout and a max-row cap are set, and the transaction is
 * always rolled back. Failures are translated into a short {@link BusinessException}; the real cause
 * is logged, never returned.
 */
@Component
public class ReadOnlySqlExecutor {

    private static final Logger log = LoggerFactory.getLogger(ReadOnlySqlExecutor.class);

    private final DataSource dataSource;
    private final int maxRows;
    private final int timeoutSeconds;

    public ReadOnlySqlExecutor(DataSource primary,
                               @Qualifier("aiReadOnlyDataSource") ObjectProvider<AiReadOnlyDataSource> readOnly,
                               AiProperties properties) {
        AiReadOnlyDataSource dedicated = readOnly.getIfAvailable();
        this.dataSource = dedicated != null ? dedicated.dataSource() : primary;
        this.maxRows = properties.sql().maxRows();
        this.timeoutSeconds = properties.sql().timeoutSeconds();
        log.info("AI SQL executor using {} data source (maxRows={}, timeout={}s)",
                dedicated != null ? "dedicated read-only" : "primary", maxRows, timeoutSeconds);
    }

    /** Executes an already-guarded statement. */
    public QueryResult execute(String guardedSql) {
        try (Connection connection = dataSource.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            try {
                connection.setReadOnly(true);
                connection.setAutoCommit(false);
                try (Statement statement = connection.createStatement()) {
                    statement.setQueryTimeout(timeoutSeconds);
                    statement.setMaxRows(maxRows);
                    try (ResultSet rs = statement.executeQuery(guardedSql)) {
                        return read(rs);
                    }
                }
            } finally {
                try {
                    connection.rollback();
                    connection.setAutoCommit(originalAutoCommit);
                    connection.setReadOnly(false);
                } catch (SQLException ignored) {
                    // the connection is being returned to the pool; nothing useful to do
                }
            }
        } catch (SQLException e) {
            log.warn("AI query failed (SQLState {}): {}", e.getSQLState(), e.getMessage());
            if ("57014".equals(e.getSQLState())) {
                throw new BusinessException("The query took too long and was cancelled. Try a narrower question.");
            }
            throw new BusinessException("The query could not be run. Try rephrasing the question.");
        } catch (RuntimeException e) {
            log.warn("AI query failed unexpectedly", e);
            throw new BusinessException("The query could not be run. Try rephrasing the question.");
        }
    }

    private QueryResult read(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int count = meta.getColumnCount();
        List<String> columns = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            columns.add(meta.getColumnLabel(i));
        }
        List<List<Object>> rows = new ArrayList<>();
        while (rs.next() && rows.size() < maxRows) {
            List<Object> row = new ArrayList<>(count);
            for (int i = 1; i <= count; i++) {
                row.add(toJson(rs.getObject(i)));
            }
            rows.add(row);
        }
        return new QueryResult(columns, rows);
    }

    /** Converts driver types into values Jackson serialises predictably. */
    static Object toJson(Object value) {
        if (value == null || value instanceof Number && !(value instanceof BigDecimal)
                || value instanceof Boolean || value instanceof String) {
            return value;
        }
        if (value instanceof BigDecimal bd) {
            return bd.scale() > 2 ? bd.setScale(2, java.math.RoundingMode.HALF_UP) : bd;
        }
        if (value instanceof java.sql.Date d) {
            return d.toLocalDate().toString();
        }
        if (value instanceof Timestamp ts) {
            return ts.toInstant().toString();
        }
        if (value instanceof Time t) {
            return t.toString();
        }
        return value.toString();
    }
}
