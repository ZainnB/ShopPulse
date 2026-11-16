package com.shoppulse.cart;

import jakarta.validation.constraints.Min;

/** Validated payload for setting a line's quantity; zero removes the line. */
public record UpdateCartItemRequest(@Min(0) int quantity) {
}
