package com.n11bootcamp.cart.client;

import java.math.BigDecimal;

public record ProductInfo(
    Long id,
    String name,
    BigDecimal basePrice,
    boolean active
) {}
