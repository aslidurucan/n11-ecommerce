package com.n11bootcamp.order.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.List;

public record OrderCreatedEvent(
    String eventId,
    Long orderId,
    String userId,
    String username,
    BigDecimal totalAmount,
    String currency,
    List<Item> items,
    Instant occurredAt
) {
    public record Item(Long productId, Integer quantity) {}

    public static OrderCreatedEvent of(Long orderId, String userId, String username,
                                        BigDecimal totalAmount, String currency,
                                        List<Item> items) {
        return new OrderCreatedEvent(UUID.randomUUID().toString(), orderId, userId, username,
            totalAmount, currency, items, Instant.now());
    }
}
