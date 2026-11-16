package com.shoppulse.analytics;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnomalyDetectorTest {

    private static final LocalDate START = LocalDate.of(2025, 1, 1);
    private final AnomalyDetector detector = new AnomalyDetector();

    /** Builds a series with a small deterministic wobble around {@code base}. */
    private static List<DailyPoint> series(int days, double base) {
        List<DailyPoint> out = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            out.add(point(i, base + (i % 5) - 2));
        }
        return out;
    }

    private static DailyPoint point(int dayIndex, double revenue) {
        return new DailyPoint(START.plusDays(dayIndex), BigDecimal.valueOf(revenue), 1, 1);
    }

    @Test
    void flatSeriesProducesNoFlags() {
        List<DailyPoint> flat = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            flat.add(point(i, 100));
        }
        assertTrue(detector.detect(flat).isEmpty());
    }

    @Test
    void gentleWobbleProducesNoFlags() {
        assertTrue(detector.detect(series(40, 1000)).isEmpty());
    }

    @Test
    void hugeSpikeIsFlaggedAsHighSpike() {
        List<DailyPoint> s = series(40, 1000);
        s.set(35, point(35, 5000));

        List<AnomalyFlag> flags = detector.detect(s);

        assertEquals(1, flags.size());
        AnomalyFlag flag = flags.get(0);
        assertEquals(START.plusDays(35), flag.date());
        assertEquals("SPIKE", flag.direction());
        assertEquals("HIGH", flag.severity());
        assertTrue(flag.zScore() >= 3.0);
        assertEquals(0, new BigDecimal("5000.00").compareTo(flag.revenue()));
    }

    @Test
    void crashDayIsFlaggedAsDrop() {
        List<DailyPoint> s = series(40, 1000);
        s.set(30, point(30, 0));

        List<AnomalyFlag> flags = detector.detect(s);

        assertEquals(1, flags.size());
        assertEquals("DROP", flags.get(0).direction());
        assertTrue(flags.get(0).zScore() < 0);
    }

    @Test
    void moderateDeviationIsModerateSeverity() {
        // Window values alternate 98/102 (sample sd ~2.0); 105.5 is roughly 2.7 sd above the mean of 100.
        List<DailyPoint> s = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            s.add(point(i, i % 2 == 0 ? 98 : 102));
        }
        s.add(point(20, 105.5));

        List<AnomalyFlag> flags = detector.detect(s);

        assertEquals(1, flags.size());
        assertEquals("MODERATE", flags.get(0).severity());
        assertEquals("SPIKE", flags.get(0).direction());
    }

    @Test
    void seriesShorterThanMinimumHistoryProducesNoFlags() {
        List<DailyPoint> s = new ArrayList<>();
        for (int i = 0; i < AnomalyDetector.MIN_HISTORY; i++) {
            s.add(point(i, i == AnomalyDetector.MIN_HISTORY - 1 ? 100000 : 100 + i));
        }
        // Exactly MIN_HISTORY points means the last day has only MIN_HISTORY-1 days of history.
        assertTrue(detector.detect(s).isEmpty());
        assertTrue(detector.detect(List.of()).isEmpty());
    }

    @Test
    void zeroVarianceWindowDoesNotThrowAndSkipsTheDay() {
        List<DailyPoint> s = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            s.add(point(i, 100));
        }
        s.add(point(20, 900)); // a "spike" after a perfectly flat window: undefined z, so skipped

        List<AnomalyFlag> flags = assertDoesNotThrow(() -> detector.detect(s));

        assertTrue(flags.isEmpty());
    }

    @Test
    void unsortedInputIsHandled() {
        List<DailyPoint> s = series(40, 1000);
        s.set(35, point(35, 5000));
        java.util.Collections.reverse(s);

        assertEquals(1, detector.detect(s).size());
    }
}
