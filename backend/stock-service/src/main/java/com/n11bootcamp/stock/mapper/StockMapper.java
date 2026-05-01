package com.n11bootcamp.stock.mapper;

import com.n11bootcamp.stock.dto.StockResponse;
import com.n11bootcamp.stock.entity.ProductStock;
import org.springframework.stereotype.Component;


@Component
public class StockMapper {


    public StockResponse toResponse(ProductStock stock) {
        return new StockResponse(
            stock.getProductId(),
            stock.getAvailableQuantity(),
            stock.getReservedQuantity()
        );
    }
}
