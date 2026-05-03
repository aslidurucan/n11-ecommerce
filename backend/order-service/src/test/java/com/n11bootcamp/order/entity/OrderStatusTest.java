package com.n11bootcamp.order.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusTest {

    @Test
    void isTerminal_returnsTrue_forCompleted() {
        assertThat(OrderStatus.COMPLETED.isTerminal()).isTrue();
    }

    @Test
    void isTerminal_returnsTrue_forCancelled() {
        assertThat(OrderStatus.CANCELLED.isTerminal()).isTrue();
    }

    @Test
    void isTerminal_returnsFalse_forPending() {
        assertThat(OrderStatus.PENDING.isTerminal()).isFalse();
    }

    @Test
    void isTerminal_returnsFalse_forStockReserved() {
        assertThat(OrderStatus.STOCK_RESERVED.isTerminal()).isFalse();
    }

    @Test
    void isTerminal_returnsFalse_forStockInsufficient() {
        assertThat(OrderStatus.STOCK_INSUFFICIENT.isTerminal()).isFalse();
    }

    @Test
    void isTerminal_returnsFalse_forPaymentProcessing() {
        assertThat(OrderStatus.PAYMENT_PROCESSING.isTerminal()).isFalse();
    }

    @Test
    void isTerminal_returnsFalse_forPaymentFailed() {
        assertThat(OrderStatus.PAYMENT_FAILED.isTerminal()).isFalse();
    }
}
