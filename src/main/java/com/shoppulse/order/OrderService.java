package com.shoppulse.order;

import com.shoppulse.auth.CurrentUserService;
import com.shoppulse.auth.Role;
import com.shoppulse.auth.User;
import com.shoppulse.common.NotFoundException;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Order read access with the ownership rule: customers see only their own, merchants any. */
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final CurrentUserService currentUserService;

    public OrderService(OrderRepository orderRepository, CurrentUserService currentUserService) {
        this.orderRepository = orderRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> listMine() {
        User user = currentUserService.requireCurrentUser();
        return orderRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(OrderResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getById(Long id) {
        User user = currentUserService.requireCurrentUser();
        Order order = orderRepository.findWithItemsById(id)
                .orElseThrow(() -> NotFoundException.of("Order", id));
        boolean owner = order.getUser().getId().equals(user.getId());
        if (!owner && user.getRole() != Role.MERCHANT) {
            throw new AccessDeniedException("Order " + id + " belongs to another user");
        }
        return OrderResponse.from(order);
    }
}
