package com.n11bootcamp.notification.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderNotification(
    Long orderId,
    String status,
    String userId,
    BigDecimal totalAmount,
    String detail,
    Instant occurredAt
) {}
