package com.shoppulse.order;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Checkout and order history for the authenticated caller. */
@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders")
public class OrderController {

    private final CheckoutService checkoutService;
    private final OrderService orderService;

    public OrderController(CheckoutService checkoutService, OrderService orderService) {
        this.checkoutService = checkoutService;
        this.orderService = orderService;
    }

    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Check out my cart (mock payment: order is PAID immediately)")
    public OrderResponse checkout() {
        return checkoutService.checkout();
    }

    @GetMapping
    @Operation(summary = "List my orders, newest first")
    public List<OrderResponse> listMine() {
        return orderService.listMine();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an order (own orders, or any for merchants)")
    public OrderResponse get(@PathVariable Long id) {
        return orderService.getById(id);
    }
}
