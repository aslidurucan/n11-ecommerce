package com.n11bootcamp.order.event;

import java.time.Instant;
import java.util.UUID;
import java.util.List;

public record StockRejectedEvent(
    String eventId,
    Long orderId,
    String reason,
    List<Long> insufficientProductIds,
    Instant occurredAt
) {
    public static StockRejectedEvent of(Long orderId, String reason, List<Long> insufficientIds) {
        return new StockRejectedEvent(UUID.randomUUID().toString(), orderId, reason, insufficientIds, Instant.now());
    }
}
