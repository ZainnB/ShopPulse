package com.shoppulse.ai;

/**
 * The model's entire view of the database. Kept as one hand-written constant so what the model
 * can see is reviewable in one place and cannot drift to include base tables.
 */
public final class SchemaDescriber {

    private static final String SCHEMA = """
            PostgreSQL read-only analytics views (the ONLY relations you may query).
            Revenue counts only PAID, SHIPPED and DELIVERED orders; PENDING and CANCELLED orders are excluded everywhere.
            Money is in dollars. Dates are calendar dates.

            v_sales_by_day: one row per day that had sales.
              sale_date date, revenue numeric, order_count bigint, units_sold bigint, unique_customers bigint

            v_sales_by_category: one row per day and category.
              sale_date date, category_id bigint, category_name text, revenue numeric, units_sold bigint, order_count bigint

            v_product_sales_daily: one row per day and product.
              sale_date date, product_id bigint, product_name text, category_name text, revenue numeric, units_sold bigint, order_count bigint

            v_top_products: one row per product, all-time totals (revenue/units are 0 or NULL when never sold).
              product_id bigint, product_name text, category_name text, current_price numeric, stock_remaining int, revenue numeric, units_sold bigint, order_count bigint

            v_customer_summary: one row per customer, lifetime totals.
              customer_id bigint, email text, display_name text, signed_up_on date, order_count bigint, lifetime_revenue numeric, last_order_date date
            """;

    private SchemaDescriber() {
    }

    public static String describe() {
        return SCHEMA;
    }
}
