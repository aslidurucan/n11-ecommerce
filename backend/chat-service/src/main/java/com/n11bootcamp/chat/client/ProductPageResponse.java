package com.n11bootcamp.chat.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductPageResponse(
        List<ProductDto> content,
        Long totalElements,
        Integer totalPages
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProductDto(
            Long id,
            String name,
            String description,
            String category,
            String brand,
            BigDecimal basePrice,
            String imageUrl,
            Boolean active
    ) {
    }
}