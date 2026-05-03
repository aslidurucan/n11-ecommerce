package com.n11bootcamp.notification.event;

import java.time.Instant;

public record OrderCancelledEvent(
    String eventId,
    Long orderId,
    String userEmail,
    String userId,
    String reason,
    Instant occurredAt
) {}
