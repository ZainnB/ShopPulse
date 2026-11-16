-- Defence in depth for "ask your store": a DB role that can read the five analytics
-- views and nothing else. Java-side guards (SELECT-only parsing, view allow-list,
-- read-only transaction, row cap, statement timeout) apply regardless; this role is
-- the second lock, used when shoppulse.ai.sql.readonly-url is configured.
-- Wrapped so the migration still succeeds on managed Postgres without CREATEROLE.
DO
$$
    BEGIN
        IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'shoppulse_ai_ro') THEN
            CREATE ROLE shoppulse_ai_ro LOGIN PASSWORD 'shoppulse_ai_ro';
        END IF;
        EXECUTE 'GRANT CONNECT ON DATABASE ' || quote_ident(current_database()) || ' TO shoppulse_ai_ro';
        GRANT USAGE ON SCHEMA public TO shoppulse_ai_ro;
        GRANT SELECT ON v_sales_by_day, v_sales_by_category, v_product_sales_daily,
            v_top_products, v_customer_summary TO shoppulse_ai_ro;
    EXCEPTION
        WHEN insufficient_privilege THEN
            RAISE NOTICE 'Skipping shoppulse_ai_ro role creation: insufficient privileges';
    END
$$;
