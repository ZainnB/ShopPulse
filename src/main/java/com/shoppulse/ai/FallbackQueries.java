package com.shoppulse.ai;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The built-in query library used when no language model is available: a keyword intent matcher
 * over the question text that picks one of a handful of pre-written SELECTs, so the demo always
 * answers something useful. The only value spliced into SQL is an integer day count parsed here.
 */
final class FallbackQueries {

    /** A chosen query plus how to chart it and a short description of how the question was read. */
    record Plan(String intent, String sql, String chartType, String labelColumn, String valueColumn,
                String description) {
    }

    static final int DEFAULT_DAYS = 30;
    private static final Pattern EXPLICIT_WINDOW =
            Pattern.compile("(\\d{1,3})\\s*(day|week|month)s?");

    private FallbackQueries() {
    }

    static Plan match(String question) {
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT);
        int days = windowDays(q);
        String window = "the last " + days + " days";

        if (containsAny(q, "stock", "inventory", "restock", "running out", "running low")) {
            return new Plan("low-stock",
                    "SELECT product_name, category_name, stock_remaining FROM v_top_products "
                            + "WHERE stock_remaining <= 10 ORDER BY stock_remaining ASC, product_name LIMIT 15",
                    "bar", "product_name", "stock_remaining", "products with 10 or fewer units left");
        }
        if (containsAny(q, "customer", "buyer", "shopper", "repeat", "loyal")) {
            return new Plan("top-customers",
                    "SELECT display_name, order_count, lifetime_revenue, last_order_date FROM v_customer_summary "
                            + "WHERE order_count > 0 ORDER BY lifetime_revenue DESC LIMIT 10",
                    "bar", "display_name", "lifetime_revenue", "top customers by lifetime revenue");
        }
        if (containsAny(q, "categor", "department")) {
            return new Plan("category-revenue",
                    "SELECT category_name, SUM(revenue) AS revenue, SUM(units_sold) AS units_sold "
                            + "FROM v_sales_by_category WHERE sale_date >= CURRENT_DATE - " + days
                            + " GROUP BY category_name ORDER BY revenue DESC",
                    "pie", "category_name", "revenue", "revenue by category over " + window);
        }
        if (containsAny(q, "best day", "worst day", "busiest", "which day")) {
            return trend(days, window);
        }
        if (containsAny(q, "product", "item", "best sell", "bestsell", "top sell", "top", "best", "worst")) {
            return new Plan("top-products",
                    "SELECT product_name, category_name, SUM(revenue) AS revenue, SUM(units_sold) AS units_sold "
                            + "FROM v_product_sales_daily WHERE sale_date >= CURRENT_DATE - " + days
                            + " GROUP BY product_name, category_name ORDER BY revenue DESC LIMIT 10",
                    "bar", "product_name", "revenue", "top products by revenue over " + window);
        }
        if (containsAny(q, "order", "aov", "basket")) {
            return new Plan("orders",
                    "SELECT sale_date, order_count, ROUND(revenue / NULLIF(order_count, 0), 2) AS avg_order_value "
                            + "FROM v_sales_by_day WHERE sale_date >= CURRENT_DATE - " + days + " ORDER BY sale_date",
                    "line", "sale_date", "order_count", "daily orders and average order value over " + window);
        }
        if (containsAny(q, "revenue", "sales", "sold", "trend", "income", "earn", "money", "growth", "daily")) {
            return trend(days, window);
        }
        return new Plan("summary",
                "SELECT COALESCE(SUM(revenue), 0) AS revenue, COALESCE(SUM(order_count), 0) AS orders, "
                        + "COALESCE(SUM(units_sold), 0) AS units_sold, "
                        + "ROUND(COALESCE(SUM(revenue), 0) / NULLIF(SUM(order_count), 0), 2) AS avg_order_value "
                        + "FROM v_sales_by_day WHERE sale_date >= CURRENT_DATE - " + days,
                "none", null, null, "a store summary for " + window);
    }

    private static Plan trend(int days, String window) {
        return new Plan("revenue-trend",
                "SELECT sale_date, revenue, order_count FROM v_sales_by_day WHERE sale_date >= CURRENT_DATE - "
                        + days + " ORDER BY sale_date",
                "line", "sale_date", "revenue", "daily revenue over " + window);
    }

    /** Parses "last 14 days", "2 weeks", "this month" ... into a day count, clamped to 1..365. */
    static int windowDays(String lowerCaseQuestion) {
        Matcher m = EXPLICIT_WINDOW.matcher(lowerCaseQuestion);
        if (m.find()) {
            int n = Integer.parseInt(m.group(1));
            int days = switch (m.group(2)) {
                case "week" -> n * 7;
                case "month" -> n * 30;
                default -> n;
            };
            return Math.max(1, Math.min(365, days));
        }
        if (containsAny(lowerCaseQuestion, "year")) {
            return 365;
        }
        if (containsAny(lowerCaseQuestion, "quarter")) {
            return 90;
        }
        if (containsAny(lowerCaseQuestion, "week")) {
            return 7;
        }
        if (containsAny(lowerCaseQuestion, "today", "yesterday")) {
            return 2;
        }
        return DEFAULT_DAYS;
    }

    private static boolean containsAny(String text, String... needles) {
        for (String n : needles) {
            if (text.contains(n)) {
                return true;
            }
        }
        return false;
    }
}
