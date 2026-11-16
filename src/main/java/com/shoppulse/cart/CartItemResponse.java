package com.shoppulse.cart;

import java.math.BigDecimal;

/** One cart line as shown to the client, including live stock so the UI can cap quantity. */
public record CartItemResponse(Long productId, String name, BigDecimal unitPrice, int quantity,
                               BigDecimal lineTotal, int stockAvailable) {
}
