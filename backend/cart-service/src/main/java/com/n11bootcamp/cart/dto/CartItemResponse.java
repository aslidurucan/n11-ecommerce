package com.n11bootcamp.cart.dto;

import java.math.BigDecimal;

public record CartItemResponse(
    Long productId,
    String productName,
    BigDecimal unitPrice,
    Integer quantity,
    BigDecimal totalPrice
) {}
