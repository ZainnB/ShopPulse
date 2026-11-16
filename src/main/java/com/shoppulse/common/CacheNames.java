package com.shoppulse.common;

/** Cache names used by the dashboard. All are evicted when an order is placed. */
public final class CacheNames {
    public static final String KPIS = "kpis";
    public static final String SALES_SERIES = "salesSeries";
    public static final String CATEGORY_SALES = "categorySales";
    public static final String TOP_PRODUCTS = "topProducts";
    public static final String ANOMALIES = "anomalies";

    public static final String[] ALL = {KPIS, SALES_SERIES, CATEGORY_SALES, TOP_PRODUCTS, ANOMALIES};

    private CacheNames() {
    }
}
