package com.n11bootcamp.order.dto;

import com.n11bootcamp.order.entity.OrderStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Getter
@Builder
public class OrderResponse {

    private final Long id;
    private final String userId;
    private final String idempotencyKey;
    private final OrderStatus status;
    private final BigDecimal totalAmount;
    private final String currency;
    private final Instant createdAt;
    private final List<Item> items;

    @Getter
    @Builder
    public static class Item {
        private final Long productId;
        private final String productName;
        private final BigDecimal unitPrice;
        private final Integer quantity;
    }
}
