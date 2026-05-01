package com.n11bootcamp.cart.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record UpdateQuantityRequest(

    @NotNull(message = "Adet zorunludur")
    @Positive(message = "Adet pozitif olmalıdır")
    Integer quantity
) {}
