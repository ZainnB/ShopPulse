package com.shoppulse.cart;

import com.shoppulse.auth.CurrentUserService;
import com.shoppulse.auth.User;
import com.shoppulse.catalog.Product;
import com.shoppulse.catalog.ProductRepository;
import com.shoppulse.common.BusinessException;
import com.shoppulse.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cart rules in one place: every operation acts on the authenticated caller's own cart
 * (never a client-supplied user id) and stock is validated before a line is accepted.
 */
@Service
public class CartService {

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final CurrentUserService currentUserService;

    public CartService(CartRepository cartRepository,
                       ProductRepository productRepository,
                       CurrentUserService currentUserService) {
        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public CartResponse getCart() {
        return CartResponse.from(getOrCreateCart());
    }

    @Transactional
    public CartResponse addItem(Long productId, int quantity) {
        if (quantity <= 0) {
            throw new BusinessException("Quantity must be positive");
        }
        Cart cart = getOrCreateCart();
        Product product = findPurchasableProduct(productId);
        int alreadyInCart = cart.findItem(productId).map(CartItem::getQuantity).orElse(0);
        requireStock(product, alreadyInCart + quantity);
        cart.addOrIncrement(product, quantity);
        return CartResponse.from(cart);
    }

    @Transactional
    public CartResponse setQuantity(Long productId, int quantity) {
        if (quantity < 0) {
            throw new BusinessException("Quantity cannot be negative");
        }
        Cart cart = getOrCreateCart();
        if (quantity == 0) {
            cart.removeProduct(productId);
            return CartResponse.from(cart);
        }
        CartItem item = cart.findItem(productId)
                .orElseThrow(() -> new NotFoundException("Product " + productId + " is not in your cart"));
        requireStock(findPurchasableProduct(productId), quantity);
        item.setQuantity(quantity);
        cart.touch();
        return CartResponse.from(cart);
    }

    @Transactional
    public CartResponse removeItem(Long productId) {
        Cart cart = getOrCreateCart();
        cart.removeProduct(productId);
        return CartResponse.from(cart);
    }

    @Transactional
    public void clear() {
        getOrCreateCart().clear();
    }

    private Cart getOrCreateCart() {
        User user = currentUserService.requireCurrentUser();
        return cartRepository.findByUserId(user.getId()).orElseGet(() -> {
            Cart cart = new Cart(user);
            cartRepository.save(cart);
            return cart;
        });
    }

    private Product findPurchasableProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> NotFoundException.of("Product", productId));
        if (!product.isActive()) {
            throw new BusinessException("'" + product.getName() + "' is no longer available");
        }
        return product;
    }

    private static void requireStock(Product product, int requested) {
        if (requested > product.getStock()) {
            throw new BusinessException("Only " + product.getStock() + " of '" + product.getName()
                    + "' in stock, " + requested + " requested");
        }
    }
}
