package com.n11bootcamp.order.event;

import java.time.Instant;
import java.util.UUID;

public record PaymentFailedEvent(
    String eventId,
    Long orderId,
    String reason,
    Instant occurredAt
) {
    public static PaymentFailedEvent of(Long orderId, String reason) {
        return new PaymentFailedEvent(UUID.randomUUID().toString(), orderId, reason, Instant.now());
    }
}
