package com.n11bootcamp.order.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    @Test
    void transitionTo_whenNotTerminal_updatesStatus() {
        Order order = buildOrder(OrderStatus.PENDING);

        order.transitionTo(OrderStatus.STOCK_RESERVED);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.STOCK_RESERVED);
    }

    @Test
    void transitionTo_chainedTransitions_workForNonTerminalPath() {
        Order order = buildOrder(OrderStatus.PENDING);

        order.transitionTo(OrderStatus.STOCK_RESERVED);
        order.transitionTo(OrderStatus.PAYMENT_PROCESSING);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PROCESSING);
    }

    @Test
    void transitionTo_whenStatusIsCompleted_throwsIllegalStateException() {
        Order order = buildOrder(OrderStatus.COMPLETED);

        assertThatThrownBy(() -> order.transitionTo(OrderStatus.CANCELLED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("terminal state");
    }

    @Test
    void transitionTo_whenStatusIsCancelled_throwsIllegalStateException() {
        Order order = buildOrder(OrderStatus.CANCELLED);

        assertThatThrownBy(() -> order.transitionTo(OrderStatus.PENDING))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("terminal state");
    }

    @Test
    void addItem_appendsItemAndSetsBackReferenceToOrder() {
        Order order = buildOrder(OrderStatus.PENDING);
        OrderItem item = OrderItem.builder()
                .productId(101L)
                .productName("iPhone")
                .unitPrice(new BigDecimal("1000.00"))
                .quantity(2)
                .build();

        order.addItem(item);

        assertThat(order.getItems()).hasSize(1);
        assertThat(item.getOrder()).isSameAs(order);
    }

    private Order buildOrder(OrderStatus status) {
        return Order.builder()
                .userId("user-1")
                .status(status)
                .build();
    }
}
