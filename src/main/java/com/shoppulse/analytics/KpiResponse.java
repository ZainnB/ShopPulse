package com.shoppulse.analytics;

import java.time.LocalDate;

/** The four dashboard KPI cards plus the window they describe. */
public record KpiResponse(KpiMetric revenue, KpiMetric orders, KpiMetric averageOrderValue,
                          KpiMetric repeatCustomerRate, LocalDate from, LocalDate to) {
}
