package com.shoppulse.analytics;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KpiMathTest {

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    @Test
    void increaseIsPositivePercent() {
        assertEquals(bd("25.0"), KpiMath.changePercent(bd("125"), bd("100")));
    }

    @Test
    void decreaseIsNegativePercent() {
        assertEquals(bd("-50.0"), KpiMath.changePercent(bd("50"), bd("100")));
    }

    @Test
    void roundsToOneDecimalHalfUp() {
        // (10.05 - 10) / 10 * 100 = 0.5 ; (2 - 3) / 3 * 100 = -33.333...
        assertEquals(bd("0.5"), KpiMath.changePercent(bd("10.05"), bd("10")));
        assertEquals(bd("-33.3"), KpiMath.changePercent(bd("2"), bd("3")));
    }

    @Test
    void previousZeroWithPositiveCurrentReports100() {
        assertEquals(bd("100.0"), KpiMath.changePercent(bd("42"), BigDecimal.ZERO));
    }

    @Test
    void previousZeroWithZeroCurrentReportsZero() {
        assertEquals(bd("0.0"), KpiMath.changePercent(BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Test
    void ratioPercentHandlesZeroWhole() {
        assertEquals(bd("0.0"), KpiMath.ratioPercent(bd("5"), BigDecimal.ZERO));
        assertEquals(bd("25.0"), KpiMath.ratioPercent(bd("1"), bd("4")));
    }

    @Test
    void moneyRoundsHalfUpToTwoDecimals() {
        assertEquals(bd("1.01"), KpiMath.money(bd("1.005")));
        assertEquals(bd("2.00"), KpiMath.money(bd("2")));
    }
}
