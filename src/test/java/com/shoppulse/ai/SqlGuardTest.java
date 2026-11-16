package com.shoppulse.ai;

import com.shoppulse.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlGuardTest {

    private final SqlGuard guard = new SqlGuard(200);

    // ------------------------------------------------------------------ accepted

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT category_name, SUM(revenue) AS revenue FROM v_sales_by_category GROUP BY category_name ORDER BY revenue DESC",
            "SELECT d.sale_date, d.revenue, c.category_name FROM v_sales_by_day d JOIN v_sales_by_category c ON c.sale_date = d.sale_date",
            "WITH recent AS (SELECT sale_date, revenue FROM v_sales_by_day WHERE sale_date >= CURRENT_DATE - 7) SELECT SUM(revenue) FROM recent",
            "WITH a AS (SELECT sale_date FROM v_sales_by_day), b AS (SELECT sale_date FROM a) SELECT * FROM b",
            "SELECT EXTRACT(month FROM sale_date) AS m, SUM(revenue) FROM v_sales_by_day GROUP BY 1",
            "SELECT * FROM (SELECT category_name, SUM(revenue) AS r FROM v_sales_by_category GROUP BY 1) t",
            "SELECT * FROM v_sales_by_day a, v_sales_by_category b WHERE a.sale_date = b.sale_date",
            "SELECT * FROM v_top_products WHERE product_name = 'update delete drop' ",
            "SELECT * FROM v_customer_summary WHERE order_count > 1 AND last_order_date IS DISTINCT FROM signed_up_on",
            "SELECT product_name FROM v_top_products WHERE product_id IN (SELECT product_id FROM v_product_sales_daily WHERE revenue > 100)",
            "select * from V_SALES_BY_DAY",
            "SELECT * FROM v_top_products WHERE product_name = 'O''Brien''s'"
    })
    void acceptsSafeSelects(String sql) {
        String out = guard.validate(sql);
        assertTrue(out.toLowerCase().startsWith("select") || out.toLowerCase().startsWith("with"), out);
        assertTrue(out.matches("(?is).*\\blimit \\d+$"), out);
    }

    @Test
    void stripsFencesAndSemicolonAndAppendsLimit() {
        String out = guard.validate("```sql\nSELECT COUNT(*)\n  FROM v_customer_summary;\n```");
        assertEquals("SELECT COUNT(*) FROM v_customer_summary LIMIT 200", out);
    }

    // ------------------------------------------------------------------ rejected

    @ParameterizedTest
    @ValueSource(strings = {
            "DROP TABLE orders",
            "SELECT 1; DELETE FROM orders",
            "SELECT * FROM v_sales_by_day; SELECT * FROM v_sales_by_day",
            "UPDATE products SET price = 0",
            "INSERT INTO products VALUES (1)",
            "TRUNCATE orders",
            "GRANT ALL ON orders TO public",
            "COPY orders TO '/tmp/x'",
            "CALL do_something()",
            "DO $$ BEGIN END $$",
            "SET ROLE postgres",
            "VACUUM",
            "SELECT * FROM v_sales_by_day -- WHERE 1=1",
            "SELECT * FROM v_sales_by_day /* hi */",
            "SELECT * FROM v_sales_by_day WHERE sale_date > '2020-01-01' --'; DROP TABLE x",
            "SELECT * FROM users",
            "SELECT * FROM orders",
            "SELECT * FROM information_schema.tables",
            "SELECT * FROM pg_catalog.pg_tables",
            "SELECT * FROM pg_user",
            "SELECT pg_sleep(10)",
            "SELECT pg_read_file('/etc/passwd')",
            "SELECT * FROM dblink('host=x', 'select 1') AS t(a int)",
            "SELECT lo_import('/etc/passwd')",
            "SELECT current_setting('server_version')",
            "SELECT * INTO backup FROM v_sales_by_day",
            "SELECT * FROM v_sales_by_day FOR UPDATE",
            "SELECT query_to_xml('select * from users', true, true, '')",
            "SELECT * FROM v_sales_by_day, orders",
            "SELECT * FROM v_sales_by_day a JOIN v_sales_by_day b ON a.sale_date = b.sale_date, users",
            "SELECT * FROM v_sales_by_day JOIN users ON true",
            "SELECT * FROM v_sales_by_day WHERE revenue > (SELECT MAX(total) FROM orders)",
            "SELECT 1 UNION SELECT password_hash FROM users",
            "SELECT * FROM public.v_sales_by_day",
            "SELECT * FROM \"orders\"",
            "SELECT * FROM generate_series(1, 10)",
            "SELECT * FROM (TABLE orders) t",
            "SELECT * FROM only orders",
            "SELECT 1",
            "SELECT * FROM v_sales_by_day WHERE category_name = 'unterminated",
            "SELECT * FROM v_sales_by_day WHERE x IN (1, 2",
            "SELECT * FROM v_sales_by_day LIMIT -1",
            "SELECT * FROM v_sales_by_day",
            "SELECT E'\\x41' FROM v_sales_by_day",
            // a CTE named like a real table must not legitimise an earlier reference to that table
            "WITH a AS (SELECT * FROM orders), orders AS (SELECT 1) SELECT * FROM a",
            // a non-recursive CTE cannot refer to itself (Postgres would read a real table of that name)
            "WITH users AS (SELECT * FROM users) SELECT * FROM users",
            "EXPLAIN SELECT * FROM v_sales_by_day",
            "WITH x AS (DELETE FROM orders RETURNING *) SELECT * FROM x"
    })
    void rejectsUnsafeStatements(String sql) {
        assertThrows(BusinessException.class, () -> guard.validate(sql), sql);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", ";", "```sql\n```"})
    void rejectsEmpty(String sql) {
        assertThrows(BusinessException.class, () -> guard.validate(sql));
    }

    @Test
    void rejectionMessageIsShortAndClear() {
        BusinessException e = assertThrows(BusinessException.class, () -> guard.validate("SELECT * FROM users"));
        assertTrue(e.getMessage().contains("'users' is not an allowed view"), e.getMessage());
    }

    @Test
    void recursiveCteMayReferenceItself() {
        String out = guard.validate("WITH RECURSIVE r AS (SELECT sale_date FROM v_sales_by_day UNION SELECT sale_date FROM r) SELECT * FROM r");
        assertTrue(out.endsWith("LIMIT 200"));
    }

    // ------------------------------------------------------------------ limits

    @Test
    void appendsLimitWhenMissing() {
        assertEquals("SELECT * FROM v_sales_by_day LIMIT 200", guard.validate("SELECT * FROM v_sales_by_day"));
    }

    @Test
    void keepsSmallerLimit() {
        assertEquals("SELECT * FROM v_sales_by_day LIMIT 10", guard.validate("SELECT * FROM v_sales_by_day LIMIT 10"));
        assertEquals("SELECT * FROM v_sales_by_day LIMIT 10 OFFSET 5",
                guard.validate("SELECT * FROM v_sales_by_day LIMIT 10 OFFSET 5"));
    }

    @Test
    void clampsLargeLimit() {
        assertEquals("SELECT * FROM v_sales_by_day LIMIT 200", guard.validate("SELECT * FROM v_sales_by_day LIMIT 5000"));
        assertEquals("SELECT * FROM v_sales_by_day LIMIT 200 OFFSET 5",
                guard.validate("SELECT * FROM v_sales_by_day LIMIT 99999999999999999999999 OFFSET 5"));
        assertEquals("SELECT * FROM v_sales_by_day limit 200", guard.validate("SELECT * FROM v_sales_by_day limit all"));
    }

    @Test
    void innerLimitDoesNotCountAsTheRowCap() {
        String out = guard.validate("SELECT * FROM (SELECT * FROM v_sales_by_day LIMIT 5) t");
        assertTrue(out.endsWith(") t LIMIT 200"), out);
    }

    @Test
    void rejectsBadMaxRows() {
        assertThrows(IllegalArgumentException.class, () -> new SqlGuard(0));
    }
}
