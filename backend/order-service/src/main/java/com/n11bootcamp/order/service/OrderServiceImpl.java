package com.n11bootcamp.order.service;

import com.n11bootcamp.order.dto.CreateOrderRequest;
import com.n11bootcamp.order.dto.OrderResponse;
import com.n11bootcamp.order.dto.ShippingAddressRequest;
import com.n11bootcamp.order.entity.*;
import com.n11bootcamp.order.event.OrderCreatedEvent;
import com.n11bootcamp.order.exception.OrderNotFoundException;
import com.n11bootcamp.order.mapper.OrderMapper;
import com.n11bootcamp.order.repository.OrderRepository;
import com.n11bootcamp.order.service.payment.CardCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OutboxEventService outboxEventService;
    private final CardCacheService cardCacheService;
    private final OrderMapper orderMapper;

    @Value("${saga.rabbit.routingKeys.stockReserveRequested}")
    private String stockReserveRequestedRoutingKey;

    @Override
    @Transactional
    public OrderResponse createOrder(String userId, String username,
                                      String idempotencyKey, CreateOrderRequest request) {
        String effectiveKey = resolveIdempotencyKey(idempotencyKey);

        var existing = orderRepository.findByIdempotencyKey(effectiveKey);
        if (existing.isPresent()) {
            log.info("Idempotent request — returning existing order {}", existing.get().getId());
            return orderMapper.toResponse(existing.get());
        }

        BigDecimal totalAmount = calculateTotal(request.getItems());
        Order savedOrder = buildAndSaveOrder(userId, username, effectiveKey, request, totalAmount);

        cardCacheService.storeCard(savedOrder.getId(), request.getCard());

        persistOrderCreatedEvent(savedOrder);

        log.info("Order created: id={}, userId={}, total={}", savedOrder.getId(), userId, totalAmount);
        return orderMapper.toResponse(savedOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long id) {
        return orderMapper.toResponse(findOrderById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> findUserOrders(String userId, Pageable pageable) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
            .map(orderMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> findAllOrders(Pageable pageable) {
        return orderRepository.findAllByOrderByCreatedAtDesc(pageable)
            .map(orderMapper::toResponse);
    }

    @Override
    @Transactional
    public void updateOrderStatus(Long orderId, OrderStatus newStatus) {
        Order order = findOrderById(orderId);
        order.transitionTo(newStatus);
        orderRepository.save(order);
        log.info("Order {} status → {}", orderId, newStatus);
    }

    @Override
    @Transactional
    public void completeOrderPayment(Long orderId, String paymentId) {
        Order order = findOrderById(orderId);
        order.transitionTo(OrderStatus.COMPLETED);
        order.setPaymentId(paymentId);
        orderRepository.save(order);
        cardCacheService.deleteCard(orderId);
        log.info("Order {} COMPLETED — paymentId={}", orderId, paymentId);
    }

    @Override
    @Transactional
    public void cancelOrder(Long orderId, String reason) {
        Order order = findOrderById(orderId);
        if (order.getStatus().isTerminal()) {
            log.warn("Order {} already terminal ({}), skip cancel", orderId, order.getStatus());
            return;
        }
        order.transitionTo(OrderStatus.CANCELLED);
        order.setPaymentFailureReason(reason);
        orderRepository.save(order);
        cardCacheService.deleteCard(orderId);
        log.info("Order {} CANCELLED — reason: {}", orderId, reason);
    }


    private String resolveIdempotencyKey(String idempotencyKey) {
        return (idempotencyKey != null && !idempotencyKey.isBlank())
            ? idempotencyKey
            : UUID.randomUUID().toString();
    }

    private BigDecimal calculateTotal(List<CreateOrderRequest.OrderItemRequest> items) {
        BigDecimal total = items.stream()
            .map(i -> i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (total.signum() <= 0) {
            throw new IllegalArgumentException("Total amount must be positive, got: " + total);
        }
        return total;
    }

    private Order buildAndSaveOrder(String userId, String username, String idempotencyKey,
                                     CreateOrderRequest request, BigDecimal totalAmount) {
        ShippingAddressRequest addr = request.getShippingAddress();

        Order order = Order.builder()
            .userId(userId)
            .username(username)
            .idempotencyKey(idempotencyKey)
            .status(OrderStatus.PENDING)
            .totalAmount(totalAmount)
            .currency("TRY")
            .shipFirstName(addr.getFirstName())
            .shipLastName(addr.getLastName())
            .shipEmail(addr.getEmail())
            .shipPhone(addr.getPhone())
            .shipAddress(addr.getAddress())
            .shipCity(addr.getCity())
            .shipCountry(addr.getCountry())
            .build();

        orderMapper.toOrderItems(request.getItems())
            .forEach(order::addItem);

        return orderRepository.save(order);
    }

    private void persistOrderCreatedEvent(Order order) {
        List<OrderCreatedEvent.Item> items = order.getItems().stream()
            .map(i -> new OrderCreatedEvent.Item(i.getProductId(), i.getQuantity()))
            .toList();

        OrderCreatedEvent event = OrderCreatedEvent.of(
            order.getId(), order.getUserId(), order.getUsername(),
            order.getTotalAmount(), order.getCurrency(), items
        );

        outboxEventService.persist(
            "Order", order.getId().toString(),
            "OrderCreatedEvent", stockReserveRequestedRoutingKey,
            event
        );
    }

    private Order findOrderById(Long id) {
        return orderRepository.findById(id)
            .orElseThrow(() -> new OrderNotFoundException(id));
    }
}
