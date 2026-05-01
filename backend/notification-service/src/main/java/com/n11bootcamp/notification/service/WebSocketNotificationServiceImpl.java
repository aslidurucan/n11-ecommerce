package com.n11bootcamp.notification.service;

import com.n11bootcamp.notification.dto.OrderNotification;
import com.n11bootcamp.notification.event.OrderCancelledEvent;
import com.n11bootcamp.notification.event.OrderCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebSocketNotificationServiceImpl implements WebSocketNotificationService {

    private static final String ORDERS_TOPIC = "/topic/orders";

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void pushOrderCompleted(OrderCompletedEvent event) {
        OrderNotification notification = new OrderNotification(
            event.orderId(), "COMPLETED", event.userId(),
            event.totalAmount(), null, event.occurredAt()
        );
        messagingTemplate.convertAndSend(ORDERS_TOPIC, notification);
        log.debug("WebSocket push: orderId={}, status=COMPLETED", event.orderId());
    }

    @Override
    public void pushOrderCancelled(OrderCancelledEvent event) {
        OrderNotification notification = new OrderNotification(
            event.orderId(), "CANCELLED", event.userId(),
            null, event.reason(), event.occurredAt()
        );
        messagingTemplate.convertAndSend(ORDERS_TOPIC, notification);
        log.debug("WebSocket push: orderId={}, status=CANCELLED", event.orderId());
    }
}
