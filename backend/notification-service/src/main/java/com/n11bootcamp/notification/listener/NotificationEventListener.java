package com.n11bootcamp.notification.listener;

import com.n11bootcamp.notification.event.OrderCancelledEvent;
import com.n11bootcamp.notification.event.OrderCompletedEvent;
import com.n11bootcamp.notification.service.MailService;
import com.n11bootcamp.notification.service.WebSocketNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {

    private final MailService mailService;
    private final WebSocketNotificationService wsService;

    @RabbitListener(queues = "${saga.rabbit.queues.orderCompleted}")
    public void onOrderCompleted(OrderCompletedEvent event) {
        log.info("[NOTIF] OrderCompleted: orderId={}, eventId={}", event.orderId(), event.eventId());

        tryPushWebSocket(() -> wsService.pushOrderCompleted(event), event.orderId(), "COMPLETED");
        mailService.sendOrderCompleted(event);
    }

    @RabbitListener(queues = "${saga.rabbit.queues.orderCancelled}")
    public void onOrderCancelled(OrderCancelledEvent event) {
        log.info("[NOTIF] OrderCancelled: orderId={}, reason={}", event.orderId(), event.reason());

        tryPushWebSocket(() -> wsService.pushOrderCancelled(event), event.orderId(), "CANCELLED");

        mailService.sendOrderCancelled(event);
    }

    private void tryPushWebSocket(Runnable pushAction, Long orderId, String status) {
        try {
            pushAction.run();
        } catch (Exception e) {
            log.error("[NOTIF] WebSocket push failed for orderId={}, status={}: {}",
                orderId, status, e.getMessage(), e);
        }
    }
}
