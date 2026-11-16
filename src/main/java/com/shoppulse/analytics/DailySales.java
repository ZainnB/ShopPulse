package com.shoppulse.analytics;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "daily_sales")
public class DailySales {

    @Id
    @Column(name = "sale_date", nullable = false)
    private LocalDate saleDate;

    @Column(nullable = false)
    private BigDecimal revenue = BigDecimal.ZERO;

    @Column(name = "order_count", nullable = false)
    private int orderCount;

    @Column(name = "units_sold", nullable = false)
    private int unitsSold;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected DailySales() {
    }

    public DailySales(LocalDate saleDate, BigDecimal revenue, int orderCount, int unitsSold) {
        this.saleDate = saleDate;
        this.revenue = revenue;
        this.orderCount = orderCount;
        this.unitsSold = unitsSold;
        this.updatedAt = Instant.now();
    }

    public LocalDate getSaleDate() { return saleDate; }
    public BigDecimal getRevenue() { return revenue; }
    public int getOrderCount() { return orderCount; }
    public int getUnitsSold() { return unitsSold; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(BigDecimal revenue, int orderCount, int unitsSold) {
        this.revenue = revenue;
        this.orderCount = orderCount;
        this.unitsSold = unitsSold;
        this.updatedAt = Instant.now();
    }
}
