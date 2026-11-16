package com.shoppulse.cart;

import java.math.BigDecimal;
import java.util.List;

/** API shape of a cart, so the entity never leaves the service layer. */
public record CartResponse(List<CartItemResponse> items, BigDecimal total, int itemCount) {

    public static CartResponse from(Cart cart) {
        List<CartItemResponse> lines = cart.getItems().stream()
                .map(i -> new CartItemResponse(
                        i.getProduct().getId(),
                        i.getProduct().getName(),
                        i.getProduct().getPrice(),
                        i.getQuantity(),
                        i.getProduct().getPrice().multiply(BigDecimal.valueOf(i.getQuantity())),
                        i.getProduct().getStock()))
                .toList();
        BigDecimal total = lines.stream().map(CartItemResponse::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        int count = lines.stream().mapToInt(CartItemResponse::quantity).sum();
        return new CartResponse(lines, total, count);
    }
}
