package com.n11bootcamp.stock.service;

import com.n11bootcamp.stock.dto.ReservationItem;
import com.n11bootcamp.stock.dto.StockResponse;
import com.n11bootcamp.stock.dto.UpdateStockRequest;

import java.util.List;


public interface StockService {

    StockResponse getStock(Long productId);

    StockResponse setStock(UpdateStockRequest request);

    StockResponse increaseStock(Long productId, int delta);

    StockResponse decreaseStock(Long productId, int delta);
    List<Long> reserveStock(Long orderId, List<ReservationItem> items);

    void releaseStock(Long orderId);

    void commitReservation(Long orderId);
}