package com.shoppulse.cart;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The caller's own cart; the user is always taken from the security context. */
@RestController
@RequestMapping("/api/v1/cart")
@Tag(name = "Cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    @Operation(summary = "Get my cart")
    public CartResponse get() {
        return cartService.getCart();
    }

    @PostMapping("/items")
    @Operation(summary = "Add a product to my cart (increments if already present)")
    public CartResponse add(@Valid @RequestBody AddCartItemRequest request) {
        return cartService.addItem(request.productId(), request.quantity());
    }

    @PutMapping("/items/{productId}")
    @Operation(summary = "Set a line's quantity; 0 removes it")
    public CartResponse update(@PathVariable Long productId, @Valid @RequestBody UpdateCartItemRequest request) {
        return cartService.setQuantity(productId, request.quantity());
    }

    @DeleteMapping("/items/{productId}")
    @Operation(summary = "Remove a product from my cart")
    public CartResponse remove(@PathVariable Long productId) {
        return cartService.removeItem(productId);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Empty my cart")
    public void clear() {
        cartService.clear();
    }
}
