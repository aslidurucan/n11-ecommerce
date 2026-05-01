package com.n11bootcamp.notification.event;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderCompletedEvent(
    String eventId,
    Long orderId,
    String userEmail,
    String userId,
    BigDecimal totalAmount,
    Instant occurredAt
) {}
