package com.n11bootcamp.order.entity;

public enum OrderStatus {

    PENDING,
    STOCK_RESERVED,
    STOCK_INSUFFICIENT,
    PAYMENT_PROCESSING,
    PAYMENT_FAILED,
    COMPLETED,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }
}
