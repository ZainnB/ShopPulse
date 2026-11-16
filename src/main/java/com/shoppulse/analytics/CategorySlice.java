package com.shoppulse.analytics;

import java.math.BigDecimal;

/** A category's share of revenue in a window, for the pie/bar chart. */
public record CategorySlice(Long categoryId, String categoryName, BigDecimal revenue,
                            int unitsSold, BigDecimal sharePercent) {
}
