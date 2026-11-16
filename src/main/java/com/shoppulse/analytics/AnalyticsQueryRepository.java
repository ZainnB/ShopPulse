package com.shoppulse.analytics;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

/**
 * Read-side SQL for the merchant dashboard. It queries the analytics views (the same
 * surface the LLM is allowed to see) so dashboard numbers and AI answers always agree.
 * Plain SQL keeps these aggregates out of the JPA entity graph.
 */
@Repository
public class AnalyticsQueryRepository {

    /** Window totals; a zero-row window yields zeros. */
    public record Totals(BigDecimal revenue, long orders, long units) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public AnalyticsQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Days that had sales in [from, to]; days without sales are absent. */
    public List<DailyPoint> findDailySeries(LocalDate from, LocalDate to) {
        return jdbc.query("""
                SELECT sale_date, revenue, order_count, units_sold
                FROM v_sales_by_day
                WHERE sale_date BETWEEN :from AND :to
                ORDER BY sale_date
                """, range(from, to),
                (rs, i) -> new DailyPoint(
                        rs.getDate("sale_date").toLocalDate(),
                        rs.getBigDecimal("revenue"),
                        rs.getInt("order_count"),
                        rs.getInt("units_sold")));
    }

    public List<CategorySlice> findCategoryTotals(LocalDate from, LocalDate to) {
        // sharePercent is filled in by the service once the grand total is known.
        return jdbc.query("""
                SELECT category_id, category_name,
                       SUM(revenue) AS revenue, SUM(units_sold) AS units_sold
                FROM v_sales_by_category
                WHERE sale_date BETWEEN :from AND :to
                GROUP BY category_id, category_name
                ORDER BY SUM(revenue) DESC
                """, range(from, to),
                (rs, i) -> new CategorySlice(
                        rs.getLong("category_id"),
                        rs.getString("category_name"),
                        rs.getBigDecimal("revenue"),
                        rs.getInt("units_sold"),
                        BigDecimal.ZERO));
    }

    public List<TopProduct> findTopProducts(LocalDate from, LocalDate to, int limit) {
        return jdbc.query("""
                SELECT product_id, product_name, category_name,
                       SUM(revenue) AS revenue, SUM(units_sold) AS units_sold
                FROM v_product_sales_daily
                WHERE sale_date BETWEEN :from AND :to
                GROUP BY product_id, product_name, category_name
                ORDER BY SUM(revenue) DESC, product_id
                LIMIT :limit
                """, range(from, to).addValue("limit", limit),
                (rs, i) -> new TopProduct(
                        rs.getLong("product_id"),
                        rs.getString("product_name"),
                        rs.getString("category_name"),
                        rs.getBigDecimal("revenue"),
                        rs.getInt("units_sold")));
    }

    public Totals findTotals(LocalDate from, LocalDate to) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(revenue), 0)     AS revenue,
                       COALESCE(SUM(order_count), 0) AS orders,
                       COALESCE(SUM(units_sold), 0)  AS units
                FROM v_sales_by_day
                WHERE sale_date BETWEEN :from AND :to
                """, range(from, to),
                (rs, i) -> new Totals(rs.getBigDecimal("revenue"), rs.getLong("orders"), rs.getLong("units")));
    }

    /**
     * Repeat-customer rate (0-100) for a window: customers with 2+ paid orders inside the
     * window divided by customers with at least one. The views only expose lifetime
     * order counts per customer and per-day unique customers, neither of which can answer
     * a windowed "distinct customers" question, so this one query reads {@code orders}
     * directly using the same revenue-status filter as the views.
     */
    public BigDecimal findRepeatCustomerRate(LocalDate from, LocalDate to) {
        return jdbc.queryForObject("""
                SELECT CASE WHEN COUNT(*) = 0 THEN 0
                            ELSE ROUND(100.0 * COUNT(*) FILTER (WHERE n >= 2) / COUNT(*), 4)
                       END
                FROM (SELECT COUNT(*) AS n
                      FROM orders o
                      WHERE o.status IN ('PAID', 'SHIPPED', 'DELIVERED')
                        AND CAST(o.created_at AS date) BETWEEN :from AND :to
                      GROUP BY o.user_id) per_customer
                """, range(from, to), BigDecimal.class);
    }

    /** Active products at or below {@code threshold} units, lowest stock first. */
    public List<LowStockItem> findLowStock(int threshold, int limit) {
        return jdbc.query("""
                SELECT p.id, p.name, c.name AS category_name, p.stock, p.price
                FROM products p
                         JOIN categories c ON c.id = p.category_id
                WHERE p.active = TRUE AND p.stock <= :threshold
                ORDER BY p.stock, p.name
                LIMIT :limit
                """, new MapSqlParameterSource("threshold", threshold).addValue("limit", limit),
                (rs, i) -> new LowStockItem(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("category_name"),
                        rs.getInt("stock"),
                        rs.getBigDecimal("price")));
    }

    private static MapSqlParameterSource range(LocalDate from, LocalDate to) {
        return new MapSqlParameterSource()
                .addValue("from", Date.valueOf(from))
                .addValue("to", Date.valueOf(to));
    }
}
