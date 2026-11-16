package com.shoppulse.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A day whose revenue is statistically unusual. All numbers are computed in Java by
 * {@link AnomalyDetector}; the LLM only ever narrates them.
 *
 * @param direction "SPIKE" or "DROP"
 * @param severity  "HIGH" when |z| &gt;= 3, otherwise "MODERATE"
 */
public record AnomalyFlag(LocalDate date, BigDecimal revenue, BigDecimal mean,
                          BigDecimal standardDeviation, double zScore,
                          String direction, String severity) {
}
