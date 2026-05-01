package com.n11bootcamp.order.messaging;

import com.n11bootcamp.order.dto.CardRequest;
import com.n11bootcamp.order.entity.Order;
import com.n11bootcamp.order.entity.OrderStatus;
import com.n11bootcamp.order.event.StockRejectedEvent;
import com.n11bootcamp.order.event.StockReservedEvent;
import com.n11bootcamp.order.repository.OrderRepository;
import com.n11bootcamp.order.service.OrderService;
import com.n11bootcamp.order.service.payment.CardCacheService;
import com.n11bootcamp.order.service.payment.PaymentProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderSagaListener {

    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final CardCacheService cardCacheService;
    private final PaymentProcessor paymentProcessor;

    @RabbitListener(queues = "${saga.rabbit.queues.stockReserved}")
    @Transactional
    public void onStockReserved(StockReservedEvent event) {
        log.info("[SAGA] StockReserved: orderId={}, eventId={}", event.orderId(), event.eventId());

        Order order = findOrder(event.orderId());

        // RabbitMQ at-least-once garantisi — duplicate gelirse atla
        if (order.getStatus() != OrderStatus.PENDING) {
            log.warn("[SAGA] Order {} not PENDING ({}), skipping duplicate", order.getId(), order.getStatus());
            return;
        }

        orderService.updateOrderStatus(order.getId(), OrderStatus.STOCK_RESERVED);

        CardRequest card = cardCacheService.getCard(order.getId());
        if (card == null) {
            log.error("[SAGA] Card cache MISS for order {} — session expired", order.getId());
            orderService.cancelOrder(order.getId(), "Payment session expired");
            return;
        }

        paymentProcessor.process(order, card);
    }

    @RabbitListener(queues = "${saga.rabbit.queues.stockRejected}")
    @Transactional
    public void onStockRejected(StockRejectedEvent event) {
        log.warn("[SAGA] StockRejected: orderId={}, reason={}", event.orderId(), event.reason());

        Order order = findOrder(event.orderId());

        if (order.getStatus().isTerminal()) {
            log.warn("[SAGA] Order {} already terminal ({}), skipping", order.getId(), order.getStatus());
            return;
        }

        orderService.cancelOrder(order.getId(), "Insufficient stock: " + event.reason());
    }

    private Order findOrder(Long orderId) {
        return orderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalStateException(
                "[SAGA] Order not found: " + orderId + " — data inconsistency"));
    }
}
