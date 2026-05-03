package com.n11bootcamp.cart.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AddItemRequest(

    @NotNull(message = "Ürün ID boş olamaz")
    Long productId,

    @NotNull(message = "Adet zorunludur")
    @Positive(message = "Adet pozitif olmalıdır")
    Integer quantity
) {}
