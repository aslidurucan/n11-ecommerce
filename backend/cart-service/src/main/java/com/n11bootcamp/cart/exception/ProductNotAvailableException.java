package com.n11bootcamp.cart.exception;

public class ProductNotAvailableException extends RuntimeException {
    public ProductNotAvailableException(Long productId) {
        super("Product is not available: " + productId);
    }
}
