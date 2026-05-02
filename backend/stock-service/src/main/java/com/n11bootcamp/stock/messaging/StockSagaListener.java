package com.n11bootcamp.stock.messaging;

import com.n11bootcamp.stock.dto.ReservationItem;
import com.n11bootcamp.stock.event.StockSagaEvents.*;
import com.n11bootcamp.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class StockSagaListener {

    private final StockService stockService;
    private final RabbitTemplate rabbitTemplate;

    @Value("${saga.rabbit.exchange}")
    private String exchange;

    @Value("${saga.rabbit.routingKeys.stockReserved}")
    private String stockReservedRk;

    @Value("${saga.rabbit.routingKeys.stockRejected}")
    private String stockRejectedRk;

    @RabbitListener(queues = "${saga.rabbit.queues.stockReserveRequested}")
    public void onOrderCreated(OrderCreatedPayload event) {
        log.info("[STOCK-SAGA] OrderCreated: orderId={}, eventId={}", event.orderId(), event.eventId());

        List<ReservationItem> items = mapToReservationItems(event.items());

        List<Long> insufficientIds = stockService.reserveStock(event.orderId(), items);

        if (insufficientIds.isEmpty()) {
            publishStockReserved(event.orderId());
        } else {
            publishStockRejected(event.orderId(), insufficientIds);
        }
    }

    @RabbitListener(queues = "${saga.rabbit.queues.paymentFailed}")
    public void onPaymentFailed(PaymentFailedPayload event) {
        log.warn("[STOCK-SAGA] PaymentFailed: orderId={}, eventId={}", event.orderId(), event.eventId());

        stockService.releaseStock(event.orderId());

        log.info("[STOCK-SAGA] Stock released for order {}", event.orderId());
    }

    @RabbitListener(queues = "${saga.rabbit.queues.orderCompleted}")
    public void onOrderCompleted(OrderCompletedPayload event) {
        log.info("[STOCK-SAGA] OrderCompleted: orderId={}, eventId={}", event.orderId(), event.eventId());
        stockService.commitReservation(event.orderId());

        log.info("[STOCK-SAGA] Stock commit done for order {}", event.orderId());
    }


    private List<ReservationItem> mapToReservationItems(List<OrderCreatedPayload.Item> eventItems) {
        return eventItems.stream()
                .map(i -> new ReservationItem(i.productId(), i.quantity()))
                .toList();
    }

    private void publishStockReserved(Long orderId) {
        var payload = new StockReservedPayload(
                UUID.randomUUID().toString(),
                orderId,
                "RESV-" + orderId,
                Instant.now()
        );
        rabbitTemplate.convertAndSend(exchange, stockReservedRk, payload);
        log.info("[STOCK-SAGA] StockReserved published for order {}", orderId);
    }

    private void publishStockRejected(Long orderId, List<Long> insufficientIds) {
        var payload = new StockRejectedPayload(
                UUID.randomUUID().toString(),
                orderId,
                "Insufficient stock for products: " + insufficientIds,
                insufficientIds,
                Instant.now()
        );
        rabbitTemplate.convertAndSend(exchange, stockRejectedRk, payload);
        log.warn("[STOCK-SAGA] StockRejected published for order {}: {}", orderId, insufficientIds);
    }
}