package com.shoppulse.ai;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The week's numbers, computed from the analytics views, in one immutable value that feeds both the
 * model prompt and the rule-based briefing so the two can never disagree about the facts.
 */
record WeekStats(String weekStart, String weekEnd, BigDecimal revenue, BigDecimal previousRevenue,
                 long orders, long previousOrders, List<CategoryChange> categories,
                 List<ProductRow> topProducts, BigDecimal returningBuyerSharePercent) {

    record CategoryChange(String name, BigDecimal revenue, BigDecimal previousRevenue, BigDecimal changePercent) {
    }

    record ProductRow(String name, BigDecimal revenue, long units) {
    }

    BigDecimal averageOrderValue() {
        return orders == 0 ? BigDecimal.ZERO : revenue.divide(BigDecimal.valueOf(orders), 2, RoundingMode.HALF_UP);
    }

    BigDecimal previousAverageOrderValue() {
        return previousOrders == 0 ? BigDecimal.ZERO
                : previousRevenue.divide(BigDecimal.valueOf(previousOrders), 2, RoundingMode.HALF_UP);
    }

    BigDecimal revenueChangePercent() {
        return percentChange(revenue, previousRevenue);
    }

    /** Best category by week-on-week change among those that had sales in the previous week; null if none. */
    CategoryChange bestCategory() {
        return categories.stream().filter(c -> c.changePercent() != null)
                .max(java.util.Comparator.comparing(CategoryChange::changePercent)).orElse(null);
    }

    CategoryChange worstCategory() {
        return categories.stream().filter(c -> c.changePercent() != null)
                .min(java.util.Comparator.comparing(CategoryChange::changePercent)).orElse(null);
    }

    /** Percentage change, or null when there is no baseline to compare against. */
    static BigDecimal percentChange(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.signum() == 0) {
            return null;
        }
        return current.subtract(previous).multiply(BigDecimal.valueOf(100)).divide(previous, 1, RoundingMode.HALF_UP);
    }

    /** Plain maps (not records) so the structure serialises with any ObjectMapper. */
    Map<String, Object> toJsonMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("weekStart", weekStart);
        m.put("weekEnd", weekEnd);
        m.put("revenue", revenue);
        m.put("previousWeekRevenue", previousRevenue);
        m.put("revenueChangePercent", revenueChangePercent());
        m.put("orders", orders);
        m.put("previousWeekOrders", previousOrders);
        m.put("averageOrderValue", averageOrderValue());
        m.put("previousWeekAverageOrderValue", previousAverageOrderValue());
        // Deliberately NOT called "repeat customer rate": the dashboard KPI of that name
        // counts customers with 2+ orders INSIDE the window, while this counts buyers
        // active this week who have ordered more than once EVER. Different questions,
        // so they get different names and never disagree in the same sentence.
        m.put("returningBuyerSharePercent", returningBuyerSharePercent);
        List<Map<String, Object>> cats = new ArrayList<>();
        for (CategoryChange c : categories) {
            Map<String, Object> cm = new LinkedHashMap<>();
            cm.put("category", c.name());
            cm.put("revenue", c.revenue());
            cm.put("previousWeekRevenue", c.previousRevenue());
            cm.put("changePercent", c.changePercent());
            cats.add(cm);
        }
        m.put("categories", cats);
        List<Map<String, Object>> prods = new ArrayList<>();
        for (ProductRow p : topProducts) {
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("product", p.name());
            pm.put("revenue", p.revenue());
            pm.put("unitsSold", p.units());
            prods.add(pm);
        }
        m.put("topProducts", prods);
        return m;
    }
}
