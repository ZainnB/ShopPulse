package com.shoppulse.order;

import java.math.BigDecimal;

/** One purchased line, priced at the unit price captured at checkout. */
public record OrderLineResponse(Long productId, String productName, int quantity,
                                BigDecimal unitPrice, BigDecimal lineTotal) {
}
