package com.shoppulse.order;

import com.shoppulse.auth.CurrentUserService;
import com.shoppulse.auth.User;
import com.shoppulse.cart.Cart;
import com.shoppulse.cart.CartItem;
import com.shoppulse.cart.CartRepository;
import com.shoppulse.catalog.Product;
import com.shoppulse.common.BusinessException;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns the caller's cart into a paid order atomically: stock, order, payment status and
 * cart clearing either all happen or none do.
 *
 * <p>Concurrency is handled with deliberate optimistic locking, not row locks. Each
 * {@link Product} carries a {@code @Version}; when two checkouts both decrement the last
 * unit, the second flush fails with {@code ObjectOptimisticLockingFailureException}, the
 * transaction rolls back, and the global handler maps it to HTTP 409 so the buyer can
 * retry. That keeps the common (uncontended) path lock-free while still making overselling
 * impossible.
 */
@Service
public class CheckoutService {

    private final CurrentUserService currentUserService;
    private final CartRepository cartRepository;
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    public CheckoutService(CurrentUserService currentUserService,
                           CartRepository cartRepository,
                           OrderRepository orderRepository,
                           ApplicationEventPublisher eventPublisher) {
        this.currentUserService = currentUserService;
        this.cartRepository = cartRepository;
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Places an order for the caller's cart. Payment is mocked: the order is marked PAID
     * immediately because real payment processing is out of scope for this project.
     */
    @Transactional
    public OrderResponse checkout() {
        User user = currentUserService.requireCurrentUser();
        Cart cart = cartRepository.findByUserId(user.getId())
                .filter(c -> !c.getItems().isEmpty())
                .orElseThrow(() -> new BusinessException("Your cart is empty"));

        Order order = new Order(user);
        for (CartItem line : cart.getItems()) {
            Product product = line.getProduct();
            if (!product.isActive()) {
                throw new BusinessException("'" + product.getName() + "' is no longer available");
            }
            product.reduceStock(line.getQuantity());
            order.addItem(new OrderItem(order, product, line.getQuantity(), product.getPrice()));
        }
        order.recalculateTotal();
        order.setStatus(OrderStatus.PAID);

        // Flush now so a concurrent-stock conflict surfaces here, before the event is published.
        orderRepository.saveAndFlush(order);
        cart.clear();

        eventPublisher.publishEvent(new OrderPlacedEvent(
                order.getId(), user.getId(), order.getTotal(), Instant.now()));
        return OrderResponse.from(order);
    }
}
