package com.n11bootcamp.order.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentCompletedEvent(
    String eventId,
    Long orderId,
    String paymentId,
    BigDecimal amount,
    Instant occurredAt
) {
    public static PaymentCompletedEvent of(Long orderId, String paymentId, BigDecimal amount) {
        return new PaymentCompletedEvent(UUID.randomUUID().toString(), orderId, paymentId, amount, Instant.now());
    }
}
