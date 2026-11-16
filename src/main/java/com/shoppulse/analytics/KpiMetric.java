package com.shoppulse.analytics;

import java.math.BigDecimal;

/**
 * One headline number with its comparison to the previous window, so the dashboard
 * can render a card without doing any arithmetic of its own.
 *
 * @param unit one of "currency", "count", "percent"
 */
public record KpiMetric(String label, BigDecimal value, BigDecimal previousValue,
                        BigDecimal changePercent, String unit) {
}
