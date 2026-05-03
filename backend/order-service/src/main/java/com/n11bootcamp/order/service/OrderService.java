package com.n11bootcamp.order.service;

import com.n11bootcamp.order.dto.CreateOrderRequest;
import com.n11bootcamp.order.dto.OrderResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


public interface OrderService {


    OrderResponse createOrder(String userId, String username, /* @Nullable */ String idempotencyKey,
                               CreateOrderRequest request);

    OrderResponse getOrder(Long id);

    Page<OrderResponse> findUserOrders(String userId, Pageable pageable);

    Page<OrderResponse> findAllOrders(Pageable pageable);

    void updateOrderStatus(Long orderId, com.n11bootcamp.order.entity.OrderStatus newStatus);

    void completeOrderPayment(Long orderId, String paymentId);

    void cancelOrder(Long orderId, String reason);
}
