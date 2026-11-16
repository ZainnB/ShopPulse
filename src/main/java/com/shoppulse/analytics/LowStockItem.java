package com.shoppulse.analytics;

import java.math.BigDecimal;

/** An active product whose stock has fallen to or below the restock threshold. */
public record LowStockItem(Long productId, String productName, String categoryName,
                           int stock, BigDecimal price) {
}
