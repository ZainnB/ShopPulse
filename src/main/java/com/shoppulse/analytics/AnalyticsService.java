package com.shoppulse.analytics;

import com.shoppulse.common.CacheNames;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns raw view rows into the dashboard's DTOs. All arithmetic (changes, shares,
 * anomaly statistics) lives here and in {@link AnomalyDetector}, never in the browser
 * or the LLM, so a given number is always computed one way.
 */
@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    /** Default window length in days, inclusive of today. */
    public static final int DEFAULT_DAYS = 30;

    private final AnalyticsQueryRepository repository;
    private final AnomalyDetector anomalyDetector = new AnomalyDetector();

    public AnalyticsService(AnalyticsQueryRepository repository) {
        this.repository = repository;
    }

    /** Resolves the default window: the last 30 days ending today (or at {@code to}). */
    public static LocalDate[] resolveRange(LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_DAYS - 1L);
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("'from' must not be after 'to'");
        }
        return new LocalDate[]{start, end};
    }

    @Cacheable(cacheNames = CacheNames.KPIS, key = "#from.toString() + ':' + #to.toString()")
    public KpiResponse kpis(LocalDate from, LocalDate to) {
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        LocalDate prevTo = from.minusDays(1);
        LocalDate prevFrom = prevTo.minusDays(days - 1);

        var cur = repository.findTotals(from, to);
        var prev = repository.findTotals(prevFrom, prevTo);

        BigDecimal curRevenue = KpiMath.money(cur.revenue());
        BigDecimal prevRevenue = KpiMath.money(prev.revenue());
        BigDecimal curOrders = BigDecimal.valueOf(cur.orders());
        BigDecimal prevOrders = BigDecimal.valueOf(prev.orders());
        BigDecimal curAov = average(cur.revenue(), cur.orders());
        BigDecimal prevAov = average(prev.revenue(), prev.orders());
        BigDecimal curRepeat = repository.findRepeatCustomerRate(from, to).setScale(1, RoundingMode.HALF_UP);
        BigDecimal prevRepeat = repository.findRepeatCustomerRate(prevFrom, prevTo).setScale(1, RoundingMode.HALF_UP);

        return new KpiResponse(
                metric("Revenue", curRevenue, prevRevenue, "currency"),
                metric("Orders", curOrders, prevOrders, "count"),
                metric("Average order value", curAov, prevAov, "currency"),
                metric("Repeat customer rate", curRepeat, prevRepeat, "percent"),
                from, to);
    }

    @Cacheable(cacheNames = CacheNames.SALES_SERIES, key = "#from.toString() + ':' + #to.toString()")
    public List<DailyPoint> series(LocalDate from, LocalDate to) {
        return zeroFilled(repository.findDailySeries(from, to), from, to);
    }

    @Cacheable(cacheNames = CacheNames.CATEGORY_SALES, key = "#from.toString() + ':' + #to.toString()")
    public List<CategorySlice> byCategory(LocalDate from, LocalDate to) {
        List<CategorySlice> rows = repository.findCategoryTotals(from, to);
        BigDecimal total = rows.stream().map(CategorySlice::revenue).reduce(BigDecimal.ZERO, BigDecimal::add);
        return rows.stream()
                .map(r -> new CategorySlice(r.categoryId(), r.categoryName(), KpiMath.money(r.revenue()),
                        r.unitsSold(), KpiMath.ratioPercent(r.revenue(), total)))
                .toList();
    }

    @Cacheable(cacheNames = CacheNames.TOP_PRODUCTS,
            key = "#from.toString() + ':' + #to.toString() + ':' + #limit")
    public List<TopProduct> topProducts(LocalDate from, LocalDate to, int limit) {
        int bounded = Math.max(1, Math.min(limit, 100));
        return repository.findTopProducts(from, to, bounded).stream()
                .map(p -> new TopProduct(p.productId(), p.productName(), p.categoryName(),
                        KpiMath.money(p.revenue()), p.unitsSold()))
                .toList();
    }

    /** Not cached: stock changes with every checkout and the query is a cheap indexed read. */
    public List<LowStockItem> lowStock(int threshold) {
        return repository.findLowStock(Math.max(0, threshold), 100);
    }

    /**
     * Anomalies whose date falls inside [from, to]. The detector is fed the preceding
     * {@link AnomalyDetector#WINDOW} days as well so the first days of the window have
     * history to be judged against.
     */
    @Cacheable(cacheNames = CacheNames.ANOMALIES, key = "#from.toString() + ':' + #to.toString()")
    public List<AnomalyFlag> anomalies(LocalDate from, LocalDate to) {
        LocalDate historyStart = from.minusDays(AnomalyDetector.WINDOW);
        List<DailyPoint> full = zeroFilled(repository.findDailySeries(historyStart, to), historyStart, to);
        return anomalyDetector.detect(full).stream()
                .filter(f -> !f.date().isBefore(from))
                .toList();
    }

    private static KpiMetric metric(String label, BigDecimal value, BigDecimal previous, String unit) {
        return new KpiMetric(label, value, previous, KpiMath.changePercent(value, previous), unit);
    }

    private static BigDecimal average(BigDecimal revenue, long orders) {
        if (orders == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return revenue.divide(BigDecimal.valueOf(orders), 2, RoundingMode.HALF_UP);
    }

    private static List<DailyPoint> zeroFilled(List<DailyPoint> rows, LocalDate from, LocalDate to) {
        Map<LocalDate, DailyPoint> byDate = new HashMap<>();
        rows.forEach(r -> byDate.put(r.date(), r));
        List<DailyPoint> out = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            DailyPoint p = byDate.get(d);
            out.add(p == null
                    ? new DailyPoint(d, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), 0, 0)
                    : new DailyPoint(d, KpiMath.money(p.revenue()), p.orders(), p.units()));
        }
        return out;
    }
}
