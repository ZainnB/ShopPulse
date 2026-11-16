package com.shoppulse.analytics;

import java.math.BigDecimal;

/** A best-selling product in a window. */
public record TopProduct(Long productId, String productName, String categoryName,
                         BigDecimal revenue, int unitsSold) {
}
