package com.n11bootcamp.stock.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockResponse implements Serializable {
    private Long productId;
    private Integer availableQuantity;
    private Integer reservedQuantity;
}
