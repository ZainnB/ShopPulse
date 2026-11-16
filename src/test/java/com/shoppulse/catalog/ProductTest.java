package com.shoppulse.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shoppulse.common.BusinessException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ProductTest {

    private Product product(int stock) {
        return new Product("Mug", "A mug", new BigDecimal("9.99"), stock, new Category("Home", "home"));
    }

    @Test
    void reduceStockDecrements() {
        Product p = product(5);
        p.reduceStock(3);
        assertThat(p.getStock()).isEqualTo(2);
    }

    @Test
    void reduceStockRefusesToGoBelowZero() {
        Product p = product(2);
        assertThatThrownBy(() -> p.reduceStock(3)).isInstanceOf(BusinessException.class);
        assertThat(p.getStock()).isEqualTo(2);
    }

    @Test
    void reduceStockRejectsNonPositiveQuantity() {
        Product p = product(2);
        assertThatThrownBy(() -> p.reduceStock(0)).isInstanceOf(BusinessException.class);
    }
}
