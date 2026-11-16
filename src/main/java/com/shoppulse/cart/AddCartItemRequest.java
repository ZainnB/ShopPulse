package com.shoppulse.cart;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Validated payload for adding a product to the cart. */
public record AddCartItemRequest(@NotNull Long productId, @Positive int quantity) {
}
