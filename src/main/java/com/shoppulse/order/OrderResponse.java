package com.shoppulse.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** API shape of an order, so the entity never leaves the service layer. */
public record OrderResponse(Long id, String status, BigDecimal total, Instant createdAt,
                            List<OrderLineResponse> items) {

    public static OrderResponse from(Order order) {
        List<OrderLineResponse> lines = order.getItems().stream()
                .map(i -> new OrderLineResponse(i.getProduct().getId(), i.getProduct().getName(),
                        i.getQuantity(), i.getUnitPrice(), i.lineTotal()))
                .toList();
        return new OrderResponse(order.getId(), order.getStatus().name(), order.getTotal(),
                order.getCreatedAt(), lines);
    }
}
