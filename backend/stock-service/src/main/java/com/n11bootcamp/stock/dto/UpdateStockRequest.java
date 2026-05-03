package com.n11bootcamp.stock.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateStockRequest {

    @NotNull(message = "productId zorunlu")
    private Long productId;

    @Min(value = 0, message = "Stok 0'dan az olamaz")
    private int quantity;
}
