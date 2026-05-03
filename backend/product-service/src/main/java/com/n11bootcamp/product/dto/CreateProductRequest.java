package com.n11bootcamp.product.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Builder
public record CreateProductRequest(

    @NotBlank(message = "Kategori boş olamaz")
    String category,

    String brand,

    @NotNull(message = "Fiyat zorunludur")
    @Positive(message = "Fiyat pozitif olmalıdır")
    BigDecimal basePrice,

    String imageUrl,

    @NotEmpty(message = "En az bir dil çevirisi gereklidir")
    @Valid
    List<TranslationRequest> translations
) {
    public record TranslationRequest(
        @NotBlank(message = "Dil kodu boş olamaz") String language,
        @NotBlank(message = "Ürün adı boş olamaz") String name,
        String description
    ) {}
}
