package com.n11bootcamp.product.dto;

import java.math.BigDecimal;

public record ProductFilterRequest(
    String category,
    String brand,
    BigDecimal minPrice,
    BigDecimal maxPrice
) {}
