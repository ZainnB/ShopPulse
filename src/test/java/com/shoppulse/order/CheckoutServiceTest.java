package com.shoppulse.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shoppulse.auth.CurrentUserService;
import com.shoppulse.auth.Role;
import com.shoppulse.auth.User;
import com.shoppulse.cart.Cart;
import com.shoppulse.cart.CartRepository;
import com.shoppulse.catalog.Category;
import com.shoppulse.catalog.Product;
import com.shoppulse.common.BusinessException;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CheckoutServiceTest {

    @Mock CurrentUserService currentUserService;
    @Mock CartRepository cartRepository;
    @Mock OrderRepository orderRepository;
    @Mock ApplicationEventPublisher eventPublisher;

    CheckoutService service;
    User user;
    Cart cart;
    Category category = new Category("Home", "home");

    @BeforeEach
    void setUp() {
        service = new CheckoutService(currentUserService, cartRepository, orderRepository, eventPublisher);
        user = new User("c@x.dev", "hash", "Cust", Role.CUSTOMER);
        ReflectionTestUtils.setField(user, "id", 1L);
        cart = new Cart(user);
        when(currentUserService.requireCurrentUser()).thenReturn(user);
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
    }

    private Product product(long id, String price, int stock) {
        Product p = new Product("P" + id, "d", new BigDecimal(price), stock, category);
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    @Test
    void happyPathBuildsTotalClearsCartAndPublishesEvent() {
        Product a = product(1, "10.00", 5);
        Product b = product(2, "2.50", 10);
        cart.addOrIncrement(a, 2);
        cart.addOrIncrement(b, 4);

        OrderResponse response = service.checkout();

        assertThat(response.status()).isEqualTo("PAID");
        assertThat(response.total()).isEqualByComparingTo("30.00");
        assertThat(response.items()).hasSize(2);
        assertThat(a.getStock()).isEqualTo(3);
        assertThat(b.getStock()).isEqualTo(6);
        assertThat(cart.getItems()).isEmpty();

        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(saved.getValue().getItems().get(0).getUnitPrice()).isEqualByComparingTo("10.00");

        ArgumentCaptor<OrderPlacedEvent> event = ArgumentCaptor.forClass(OrderPlacedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().userId()).isEqualTo(1L);
        assertThat(event.getValue().total()).isEqualByComparingTo("30.00");
    }

    @Test
    void emptyCartThrows() {
        assertThatThrownBy(() -> service.checkout()).isInstanceOf(BusinessException.class);
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void insufficientStockThrowsAndSavesNothing() {
        cart.addOrIncrement(product(1, "10.00", 1), 3);

        assertThatThrownBy(() -> service.checkout()).isInstanceOf(BusinessException.class);

        verify(orderRepository, never()).saveAndFlush(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
        assertThat(cart.getItems()).hasSize(1);
    }
}
