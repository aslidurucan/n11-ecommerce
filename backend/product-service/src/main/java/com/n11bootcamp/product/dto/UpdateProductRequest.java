package com.n11bootcamp.product.dto;

import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record UpdateProductRequest(
    String category,
    String brand,

    @Positive(message = "Fiyat pozitif olmalıdır")
    BigDecimal basePrice,

    String imageUrl,
    Boolean active
) {}
