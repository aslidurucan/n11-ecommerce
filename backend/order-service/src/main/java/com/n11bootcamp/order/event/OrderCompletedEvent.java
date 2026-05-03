package com.n11bootcamp.order.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderCompletedEvent(
    String eventId,
    Long orderId,
    String userEmail,
    String userId,
    BigDecimal totalAmount,
    Instant occurredAt
) {
    public static OrderCompletedEvent of(Long orderId, String userEmail,
                                          String userId, BigDecimal totalAmount) {
        return new OrderCompletedEvent(UUID.randomUUID().toString(), orderId, userEmail, userId, totalAmount, Instant.now());
    }
}
