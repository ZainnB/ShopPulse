package com.shoppulse.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Rolling z-score anomaly detection on daily revenue. It exists as a dependency-free
 * class so every statistic is computed deterministically in Java; the LLM never
 * calculates anything, it only describes the flags produced here.
 *
 * <p>For each day with at least {@value #MIN_HISTORY} prior days, the value is compared
 * with the mean and sample standard deviation of up to the previous {@value #WINDOW}
 * days. Days with |z| &gt;= {@value #THRESHOLD} are flagged.
 */
public class AnomalyDetector {

    public static final int WINDOW = 28;
    public static final int MIN_HISTORY = 7;
    public static final double THRESHOLD = 2.0;
    public static final double HIGH_THRESHOLD = 3.0;

    /** Below this a window is treated as constant and the day is skipped. */
    private static final double EPSILON = 1e-9;

    /**
     * @param series consecutive daily points (zero-filled); sorted by date internally
     * @return flags in chronological order
     */
    public List<AnomalyFlag> detect(List<DailyPoint> series) {
        List<DailyPoint> sorted = new ArrayList<>(series);
        sorted.sort(Comparator.comparing(DailyPoint::date));

        double[] values = new double[sorted.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = sorted.get(i).revenue().doubleValue();
        }

        List<AnomalyFlag> flags = new ArrayList<>();
        for (int i = MIN_HISTORY; i < values.length; i++) {
            int start = Math.max(0, i - WINDOW);
            int n = i - start;

            double sum = 0;
            for (int j = start; j < i; j++) {
                sum += values[j];
            }
            double mean = sum / n;

            double squares = 0;
            for (int j = start; j < i; j++) {
                double d = values[j] - mean;
                squares += d * d;
            }
            double stdDev = Math.sqrt(squares / (n - 1));
            if (stdDev < EPSILON) {
                continue; // no variation in the window: z-score is undefined
            }

            double z = (values[i] - mean) / stdDev;
            if (Math.abs(z) < THRESHOLD) {
                continue;
            }
            DailyPoint point = sorted.get(i);
            flags.add(new AnomalyFlag(
                    point.date(),
                    point.revenue().setScale(2, RoundingMode.HALF_UP),
                    BigDecimal.valueOf(mean).setScale(2, RoundingMode.HALF_UP),
                    BigDecimal.valueOf(stdDev).setScale(2, RoundingMode.HALF_UP),
                    Math.round(z * 100.0) / 100.0,
                    z > 0 ? "SPIKE" : "DROP",
                    Math.abs(z) >= HIGH_THRESHOLD ? "HIGH" : "MODERATE"));
        }
        return flags;
    }
}
