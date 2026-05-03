package com.n11bootcamp.order.event;

import java.time.Instant;
import java.util.UUID;

public record StockReservedEvent(
    String eventId,
    Long orderId,
    String reservationId,
    Instant occurredAt
) {
    public static StockReservedEvent of(Long orderId, String reservationId) {
        return new StockReservedEvent(UUID.randomUUID().toString(), orderId, reservationId, Instant.now());
    }
}
