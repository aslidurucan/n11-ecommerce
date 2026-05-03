package com.n11bootcamp.product.dto;

import lombok.Builder;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

@Builder
public record ProductResponse(
    Long id,
    String name,
    String description,
    String category,
    String brand,
    BigDecimal basePrice,
    String imageUrl,
    Boolean active,
    Instant createdAt
) implements Serializable {}
