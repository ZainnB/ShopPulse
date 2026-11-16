package com.shoppulse.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure arithmetic helpers for KPI cards, kept apart from the service so the edge cases
 * (zero previous value, rounding) can be unit tested without mocks.
 */
public final class KpiMath {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private KpiMath() {
    }

    /**
     * Percentage change from {@code previous} to {@code current}, rounded to 1 dp.
     * When the previous value is zero a ratio is undefined, so we report 100 if the
     * current value is positive (growth from nothing) and 0 otherwise.
     */
    public static BigDecimal changePercent(BigDecimal current, BigDecimal previous) {
        if (previous.signum() == 0) {
            return current.signum() > 0 ? percent(HUNDRED) : percent(BigDecimal.ZERO);
        }
        return current.subtract(previous)
                .multiply(HUNDRED)
                .divide(previous.abs(), 1, RoundingMode.HALF_UP);
    }

    /** {@code part / whole * 100} at 1 dp, or 0 when {@code whole} is zero. */
    public static BigDecimal ratioPercent(BigDecimal part, BigDecimal whole) {
        if (whole.signum() == 0) {
            return percent(BigDecimal.ZERO);
        }
        return part.multiply(HUNDRED).divide(whole, 1, RoundingMode.HALF_UP);
    }

    /** Rounds a money amount to 2 dp, HALF_UP. */
    public static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal percent(BigDecimal value) {
        return value.setScale(1, RoundingMode.HALF_UP);
    }
}
