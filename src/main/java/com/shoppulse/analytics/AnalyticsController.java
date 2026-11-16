package com.shoppulse.analytics;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** Merchant dashboard data API; thin because all logic lives in {@link AnalyticsService}. */
@RestController
@RequestMapping("/api/v1/merchant/analytics")
@PreAuthorize("hasRole('MERCHANT')")
@Tag(name = "Merchant analytics", description = "Sales KPIs, series, breakdowns and anomalies")
public class AnalyticsController {

    private final AnalyticsService service;

    public AnalyticsController(AnalyticsService service) {
        this.service = service;
    }

    @GetMapping("/kpis")
    @Operation(summary = "Headline KPIs compared with the previous equal-length window")
    public KpiResponse kpis(
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to) {
        LocalDate[] r = AnalyticsService.resolveRange(from, to);
        return service.kpis(r[0], r[1]);
    }

    @GetMapping("/series")
    @Operation(summary = "Daily revenue, orders and units (zero-filled)")
    public List<DailyPoint> series(
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to) {
        LocalDate[] r = AnalyticsService.resolveRange(from, to);
        return service.series(r[0], r[1]);
    }

    @GetMapping("/by-category")
    @Operation(summary = "Revenue share by category")
    public List<CategorySlice> byCategory(
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to) {
        LocalDate[] r = AnalyticsService.resolveRange(from, to);
        return service.byCategory(r[0], r[1]);
    }

    @GetMapping("/top-products")
    @Operation(summary = "Best-selling products by revenue")
    public List<TopProduct> topProducts(
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") int limit) {
        LocalDate[] r = AnalyticsService.resolveRange(from, to);
        return service.topProducts(r[0], r[1], limit);
    }

    @GetMapping("/low-stock")
    @Operation(summary = "Active products at or below a stock threshold")
    public List<LowStockItem> lowStock(@RequestParam(defaultValue = "10") int threshold) {
        return service.lowStock(threshold);
    }

    @GetMapping("/anomalies")
    @Operation(summary = "Days with statistically unusual revenue (rolling z-score)")
    public List<AnomalyFlag> anomalies(
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to) {
        LocalDate[] r = AnalyticsService.resolveRange(from, to);
        return service.anomalies(r[0], r[1]);
    }
}
