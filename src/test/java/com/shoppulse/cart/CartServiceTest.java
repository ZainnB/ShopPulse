package com.shoppulse.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;

import com.shoppulse.auth.CurrentUserService;
import com.shoppulse.auth.Role;
import com.shoppulse.auth.User;
import com.shoppulse.catalog.Category;
import com.shoppulse.catalog.Product;
import com.shoppulse.catalog.ProductRepository;
import com.shoppulse.common.BusinessException;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock CartRepository cartRepository;
    @Mock ProductRepository productRepository;
    @Mock CurrentUserService currentUserService;

    CartService service;
    User user;
    Cart cart;
    Product product;

    @BeforeEach
    void setUp() {
        service = new CartService(cartRepository, productRepository, currentUserService);
        user = new User("c@x.dev", "hash", "Cust", Role.CUSTOMER);
        ReflectionTestUtils.setField(user, "id", 1L);
        cart = new Cart(user);
        product = new Product("Mug", "d", new BigDecimal("10.00"), 5, new Category("Home", "home"));
        ReflectionTestUtils.setField(product, "id", 7L);

        lenient().when(currentUserService.requireCurrentUser()).thenReturn(user);
        lenient().when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        lenient().when(productRepository.findById(7L)).thenReturn(Optional.of(product));
    }

    @Test
    void addingSameProductTwiceIncrementsQuantity() {
        service.addItem(7L, 2);
        CartResponse response = service.addItem(7L, 1);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).quantity()).isEqualTo(3);
        assertThat(response.itemCount()).isEqualTo(3);
        assertThat(response.total()).isEqualByComparingTo("30.00");
    }

    @Test
    void addingMoreThanStockThrows() {
        assertThatThrownBy(() -> service.addItem(7L, 6)).isInstanceOf(BusinessException.class);
        assertThat(cart.getItems()).isEmpty();
    }

    @Test
    void addingBeyondStockAcrossCallsThrows() {
        service.addItem(7L, 4);
        assertThatThrownBy(() -> service.addItem(7L, 2)).isInstanceOf(BusinessException.class);
        assertThat(cart.findItem(7L).orElseThrow().getQuantity()).isEqualTo(4);
    }

    @Test
    void inactiveProductCannotBeAdded() {
        product.setActive(false);
        assertThatThrownBy(() -> service.addItem(7L, 1)).isInstanceOf(BusinessException.class);
    }

    @Test
    void settingQuantityToZeroRemovesLine() {
        service.addItem(7L, 2);
        CartResponse response = service.setQuantity(7L, 0);

        assertThat(response.items()).isEmpty();
        assertThat(response.itemCount()).isZero();
    }

    @Test
    void settingQuantityUpdatesLine() {
        service.addItem(7L, 2);
        CartResponse response = service.setQuantity(7L, 4);
        assertThat(response.items().get(0).quantity()).isEqualTo(4);
    }
}
