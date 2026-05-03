package com.n11bootcamp.chat.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

/**
 * Spring'in {@code Page<ProductResponse>} JSON formatını parse eder.
 *
 * <p>Spring Page'in REST üzerinden gelen yapısı şudur:</p>
 * <pre>
 * {
 *   "content": [...],
 *   "totalElements": 13,
 *   "totalPages": 2,
 *   ...
 * }
 * </pre>
 *
 * <p>Sadece ihtiyacımız olan alanları parse ediyoruz; kalanları
 * Jackson sessizce yok sayar ({@code @JsonIgnoreProperties}).</p>
 */
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