package com.n11bootcamp.order.mapper;

import com.n11bootcamp.order.dto.CreateOrderRequest;
import com.n11bootcamp.order.dto.OrderResponse;
import com.n11bootcamp.order.entity.Order;
import com.n11bootcamp.order.entity.OrderItem;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderMapper {

    public OrderResponse toResponse(Order order) {
        return OrderResponse.builder()
            .id(order.getId())
            .userId(order.getUserId())
            .idempotencyKey(order.getIdempotencyKey())
            .status(order.getStatus())
            .totalAmount(order.getTotalAmount())
            .currency(order.getCurrency())
            .createdAt(order.getCreatedAt())
            .items(toItemResponses(order.getItems()))
            .build();
    }

    public List<OrderItem> toOrderItems(List<CreateOrderRequest.OrderItemRequest> requests) {
        return requests.stream()
            .map(this::toOrderItem)
            .toList();
    }

    private List<OrderResponse.Item> toItemResponses(List<OrderItem> items) {
        return items.stream().map(this::toItemResponse).toList();
    }

    private OrderResponse.Item toItemResponse(OrderItem item) {
        return OrderResponse.Item.builder()
            .productId(item.getProductId())
            .productName(item.getProductName())
            .unitPrice(item.getUnitPrice())
            .quantity(item.getQuantity())
            .build();
    }

    private OrderItem toOrderItem(CreateOrderRequest.OrderItemRequest req) {
        return OrderItem.builder()
            .productId(req.getProductId())
            .productName(req.getProductName())
            .unitPrice(req.getUnitPrice())
            .quantity(req.getQuantity())
            .build();
    }
}
