package com.n11bootcamp.stock.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;


public class StockSagaEvents {


    public record OrderCreatedPayload(
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
    }

    public record PaymentFailedPayload(
        String eventId,
        Long orderId,
        String reason,
        Instant occurredAt
    ) {}


    public record StockReservedPayload(
        String eventId,
        Long orderId,
        String reservationId,
        Instant occurredAt
    ) {}

    public record StockRejectedPayload(
        String eventId,
        Long orderId,
        String reason,
        List<Long> insufficientProductIds,
        Instant occurredAt
    ) {}
}
