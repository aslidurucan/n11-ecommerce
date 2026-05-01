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

/**
 * Payment processor — Iyzico ödeme akışı + sonrası state güncellemeleri.
 *
 * <p><b>Transaction yönetimi:</b></p>
 * <ul>
 *   <li><b>process()</b>: @Transactional YOK — Iyzico HTTP çağrısı transaction'ı tutmamalı.</li>
 *   <li><b>handleSuccess()</b>: @Transactional — Order ve outbox event atomik.</li>
 *   <li><b>handleFailure()</b>: @Transactional — Order CANCEL + 2 outbox event atomik.</li>
 * </ul>
 */
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

    /**
     * Ana akış. @Transactional YOK — Iyzico HTTP transaction içinde olmamalı.
     * PAYMENT_PROCESSING status güncellemesi OrderService'in kendi @Transactional'ı ile.
     */
    @Override
    public void process(Order order, CardRequest card) {
        // 1. PAYMENT_PROCESSING'e geç (kendi kısa transaction'ında)
        orderService.updateOrderStatus(order.getId(), OrderStatus.PAYMENT_PROCESSING);

        // 2. Iyzico HTTP çağrısı — TRANSACTION DIŞINDA
        IyzicoPaymentService.PaymentResult result = attemptPayment(order, card);

        // 3. Sonucu işle (her biri kendi @Transactional içinde)
        if (result.success()) {
            handleSuccess(order.getId(), result.paymentId());
        } else {
            handleFailure(order.getId(), result.errorMessage());
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

    /**
     * Başarılı ödeme — Order COMPLETED + OrderCompletedEvent ATOMİK.
     * Card cache temizliği transaction dışında (Redis transaction'a katılmaz).
     */
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

        // Redis cleanup — TRANSACTION DIŞINDA (Redis transaction'a katılmaz)
        cardCacheService.deleteCard(orderId);
    }

    /**
     * Başarısız ödeme — Order CANCELLED + 2 outbox event ATOMİK.
     *
     * <p>Önceki implementation 4 ayrı transaction kullanıyordu:
     * T1 (PAYMENT_FAILED) + T2 (PaymentFailedEvent) + T3 (CANCELLED) + T4 (OrderCancelledEvent).
     * T3 fail olursa T1+T2 commit edildiği için inconsistent state oluşur.</p>
     *
     * <p>Bu yeni implementation hepsini tek transaction'da yapar — atomik.
     * Intermediate PAYMENT_FAILED state'i kaldırıldı: doğrudan CANCELLED'e geçilir.</p>
     */
    @Transactional
    public void handleFailure(Long orderId, String errorMessage) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalStateException("Order not found: " + orderId));

        // Doğrudan CANCELLED'e geç (intermediate PAYMENT_FAILED gereksiz)
        order.transitionTo(OrderStatus.CANCELLED);
        order.setPaymentFailureReason(errorMessage);
        orderRepository.save(order);

        // PaymentFailedEvent → stock-service compensation için (stoğu geri aç)
        outboxEventService.persist(
            AGGREGATE_TYPE, orderId.toString(),
            EVENT_PAYMENT_FAILED, paymentFailedRoutingKey,
            PaymentFailedEvent.of(orderId, errorMessage)
        );

        // OrderCancelledEvent → notification-service mail için
        outboxEventService.persist(
            AGGREGATE_TYPE, orderId.toString(),
            EVENT_ORDER_CANCELLED, orderCancelledRoutingKey,
            OrderCancelledEvent.of(orderId, order.getShipEmail(),
                order.getUserId(), CANCEL_REASON_PREFIX + errorMessage)
        );

        log.warn("[PAYMENT] Order {} CANCELLED — reason: {}", orderId, errorMessage);

        // Redis cleanup — TRANSACTION DIŞINDA
        cardCacheService.deleteCard(orderId);
    }
}
