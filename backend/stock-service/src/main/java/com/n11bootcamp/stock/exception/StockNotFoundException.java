package com.n11bootcamp.stock.exception;


public class StockNotFoundException extends RuntimeException {

    public StockNotFoundException(Long productId) {
        super("Stock not found for product: " + productId);
    }
}
