package com.n11bootcamp.cart.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CartResponse(
    String userId,
    List<CartItemResponse> items,
    int itemCount,
    BigDecimal grandTotal,
    Instant updatedAt
) {}
