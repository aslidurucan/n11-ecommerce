package com.n11bootcamp.order.event;

import java.time.Instant;
import java.util.UUID;

public record OrderCancelledEvent(
    String eventId,
    Long orderId,
    String userEmail,
    String userId,
    String reason,
    Instant occurredAt
) {
    public static OrderCancelledEvent of(Long orderId, String userEmail,
                                          String userId, String reason) {
        return new OrderCancelledEvent(UUID.randomUUID().toString(), orderId, userEmail, userId, reason, Instant.now());
    }
}
