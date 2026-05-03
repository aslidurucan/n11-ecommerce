package com.n11bootcamp.chat.extraction;

import java.math.BigDecimal;

public record ExtractedFilter(
        String category,
        String brand,
        BigDecimal minPrice,
        BigDecimal maxPrice
) {
    public boolean isEmpty() {
        return category == null && brand == null
                && minPrice == null && maxPrice == null;
    }
}
