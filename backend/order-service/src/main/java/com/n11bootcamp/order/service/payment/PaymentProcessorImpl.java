package com.n11bootcamp.order.service.payment;

import com.n11bootcamp.order.dto.CardRequest;
import com.n11bootcamp.order.entity.Order;
import com.n11bootcamp.order.entity.OrderStatus;
import com.n11bootcamp.order.event.OrderCancelledEvent;
import com.n11bootcamp.order.event.OrderCompletedEvent;
import com.n11bootcamp.order.event.PaymentFailedEvent;
import com.n11bootcamp.order.service.OrderService;
import com.n11bootcamp.order.service.OutboxEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentProcessorImpl implements PaymentProcessor {

    private final IyzicoPaymentService paymentService;
    private final OrderService orderService;
    private final OutboxEventService outboxEventService;

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
            handleSuccess(order, result.paymentId());
        } else {
            handleFailure(order, result.errorMessage());
        }
    }

    // ======================== PRIVATE ========================

    private IyzicoPaymentService.PaymentResult attemptPayment(Order order, CardRequest card) {
        try {
            return paymentService.charge(order, card);
        } catch (Exception e) {
            log.error("Unexpected payment exception for order {}: {}", order.getId(), e.getMessage(), e);
            return IyzicoPaymentService.PaymentResult.failure("Payment gateway error: " + e.getMessage());
        }
    }

    private void handleSuccess(Order order, String paymentId) {
        orderService.completeOrderPayment(order.getId(), paymentId);

        outboxEventService.persist(
            "Order", order.getId().toString(),
            "OrderCompletedEvent", orderCompletedRoutingKey,
            OrderCompletedEvent.of(order.getId(), order.getShipEmail(),
                order.getUserId(), order.getTotalAmount())
        );

        log.info("[PAYMENT] Order {} COMPLETED — paymentId={}", order.getId(), paymentId);
    }

    private void handleFailure(Order order, String errorMessage) {
        orderService.updateOrderStatus(order.getId(), OrderStatus.PAYMENT_FAILED);

        // stock-service bu event'i alıp stoğu geri açar (compensation)
        outboxEventService.persist(
            "Order", order.getId().toString(),
            "PaymentFailedEvent", paymentFailedRoutingKey,
            PaymentFailedEvent.of(order.getId(), errorMessage)
        );

        orderService.cancelOrder(order.getId(), "Payment failed: " + errorMessage);

        outboxEventService.persist(
            "Order", order.getId().toString(),
            "OrderCancelledEvent", orderCancelledRoutingKey,
            OrderCancelledEvent.of(order.getId(), order.getShipEmail(),
                order.getUserId(), "Payment failed: " + errorMessage)
        );

        log.warn("[PAYMENT] Order {} PAYMENT_FAILED → CANCELLED — reason: {}", order.getId(), errorMessage);
    }
}
