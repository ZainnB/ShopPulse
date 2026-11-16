package com.shoppulse.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One day of the sales time series; days without sales are zero-filled. */
public record DailyPoint(LocalDate date, BigDecimal revenue, int orders, int units) {
}
