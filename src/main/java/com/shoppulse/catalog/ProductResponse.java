package com.shoppulse.catalog;

import java.math.BigDecimal;

/** API shape of a product, so the entity never leaves the service layer. */
public record ProductResponse(Long id, String name, String description, BigDecimal price, int stock,
                              Long categoryId, String categoryName, boolean active) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(product.getId(), product.getName(), product.getDescription(),
                product.getPrice(), product.getStock(), product.getCategory().getId(),
                product.getCategory().getName(), product.isActive());
    }
}
