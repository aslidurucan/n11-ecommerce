package com.n11bootcamp.order.service.payment;

import com.n11bootcamp.order.dto.CardRequest;
import com.n11bootcamp.order.entity.Order;
import com.n11bootcamp.order.entity.OrderStatus;
import com.n11bootcamp.order.event.OrderCancelledEvent;
import com.n11bootcamp.order.event.OrderCompletedEvent;
import com.n11bootcamp.order.event.PaymentFailedEvent;
import com.n11bootcamp.order.repository.OrderRepository;
import com.n11bootcamp.order.service.OrderService;
import com.n11bootcamp.order.service.OutboxEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentProcessorImpl implements PaymentProcessor {

    private static final String AGGREGATE_TYPE = "Order";
    private static final String EVENT_PAYMENT_FAILED = "PaymentFailedEvent";
    private static final String EVENT_ORDER_COMPLETED = "OrderCompletedEvent";
    private static final String EVENT_ORDER_CANCELLED = "OrderCancelledEvent";
    private static final String CANCEL_REASON_PREFIX = "Payment failed: ";

    private final IyzicoPaymentService paymentService;
    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final OutboxEventService outboxEventService;
    private final CardCacheService cardCacheService;

    @Value("${saga.rabbit.routingKeys.paymentFailed}")
    private String paymentFailedRoutingKey;

    @Value("${saga.rabbit.routingKeys.orderCompleted}")
    private String orderCompletedRoutingKey;

    @Value("${saga.rabbit.routingKeys.orderCancelled}")
    private String orderCancelledRoutingKey;

    @Override
    public void process(Order order, CardRequest card) {
        orderService.updateOrderStatus(order.getId(), OrderStatus.PAYMENT_PROCESSING);

        IyzicoPaymentService.PaymentResult result = attemptPayment(order, card);

        if (result.success()) {
            handleSuccess(order.getId(), result.paymentId());
        } else {
            handleFailure(order.getId(), result.errorMessage());
        }
    }

    private IyzicoPaymentService.PaymentResult attemptPayment(Order order, CardRequest card) {
        try {
            return paymentService.charge(order, card);
        } catch (Exception e) {
            log.error("Unexpected payment exception for order {}: {}", order.getId(), e.getMessage(), e);
            return IyzicoPaymentService.PaymentResult.failure("Payment gateway error: " + e.getMessage());
        }
    }

    @Transactional
    public void handleSuccess(Long orderId, String paymentId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalStateException("Order not found: " + orderId));

        order.transitionTo(OrderStatus.COMPLETED);
        order.setPaymentId(paymentId);
        orderRepository.save(order);

        outboxEventService.persist(
            AGGREGATE_TYPE, orderId.toString(),
            EVENT_ORDER_COMPLETED, orderCompletedRoutingKey,
            OrderCompletedEvent.of(orderId, order.getShipEmail(),
                order.getUserId(), order.getTotalAmount())
        );

        log.info("[PAYMENT] Order {} COMPLETED — paymentId={}", orderId, paymentId);

        cardCacheService.deleteCard(orderId);
    }

    @Transactional
    public void handleFailure(Long orderId, String errorMessage) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalStateException("Order not found: " + orderId));

        order.transitionTo(OrderStatus.CANCELLED);
        order.setPaymentFailureReason(errorMessage);
        orderRepository.save(order);

        outboxEventService.persist(
            AGGREGATE_TYPE, orderId.toString(),
            EVENT_PAYMENT_FAILED, paymentFailedRoutingKey,
            PaymentFailedEvent.of(orderId, errorMessage)
        );

        outboxEventService.persist(
            AGGREGATE_TYPE, orderId.toString(),
            EVENT_ORDER_CANCELLED, orderCancelledRoutingKey,
            OrderCancelledEvent.of(orderId, order.getShipEmail(),
                order.getUserId(), CANCEL_REASON_PREFIX + errorMessage)
        );

        log.warn("[PAYMENT] Order {} CANCELLED — reason: {}", orderId, errorMessage);

        cardCacheService.deleteCard(orderId);
    }
}
