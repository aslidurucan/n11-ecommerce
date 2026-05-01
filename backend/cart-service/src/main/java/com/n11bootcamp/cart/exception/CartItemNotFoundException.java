package com.n11bootcamp.cart.exception;

public class CartItemNotFoundException extends RuntimeException {
    public CartItemNotFoundException(Long productId) {
        super("Cart item not found for productId: " + productId);
    }
}
