package com.shoppulse.analytics;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;

/**
 * Keeps the pre-aggregated {@code daily_sales} table in step with the orders tables.
 * Each recompute is an idempotent UPSERT, so it is safe to run repeatedly (event,
 * nightly heal, seeding) without double counting.
 *
 * <p>Methods use REQUIRES_NEW because they are called from an AFTER_COMMIT listener,
 * where the original transaction is already finished and cannot take new writes.
 */
@Service
public class DailySalesRollupService {

    private static final String UPSERT_DAY = """
            INSERT INTO daily_sales (sale_date, revenue, order_count, units_sold, updated_at)
            SELECT CAST(:day AS date),
                   COALESCE(SUM(oi.quantity * oi.unit_price), 0),
                   COUNT(DISTINCT o.id),
                   COALESCE(SUM(oi.quantity), 0),
                   now()
            FROM orders o
                     JOIN order_items oi ON oi.order_id = o.id
            WHERE o.status IN ('PAID', 'SHIPPED', 'DELIVERED')
              AND CAST(o.created_at AS date) = CAST(:day AS date)
            ON CONFLICT (sale_date) DO UPDATE
                SET revenue     = EXCLUDED.revenue,
                    order_count = EXCLUDED.order_count,
                    units_sold  = EXCLUDED.units_sold,
                    updated_at  = EXCLUDED.updated_at
            """;

    private static final String UPSERT_ALL = """
            INSERT INTO daily_sales (sale_date, revenue, order_count, units_sold, updated_at)
            SELECT sale_date, revenue, order_count, units_sold, now()
            FROM v_sales_by_day
            ON CONFLICT (sale_date) DO UPDATE
                SET revenue     = EXCLUDED.revenue,
                    order_count = EXCLUDED.order_count,
                    units_sold  = EXCLUDED.units_sold,
                    updated_at  = EXCLUDED.updated_at
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public DailySalesRollupService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void rebuildDay(LocalDate day) {
        upsert(day);
    }

    /** Recomputes every day in [from, to] inclusive. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void rebuildRange(LocalDate from, LocalDate to) {
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            upsert(d);
        }
    }

    /** Set-based rebuild of every day that has sales; used after bulk seeding. Returns rows written. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int rebuildAll() {
        return jdbc.update(UPSERT_ALL, new MapSqlParameterSource());
    }

    private void upsert(LocalDate day) {
        jdbc.update(UPSERT_DAY, new MapSqlParameterSource("day", Date.valueOf(day)));
    }
}
