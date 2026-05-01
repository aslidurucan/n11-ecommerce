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
        mailService.sendOrderCompleted(event);
        wsService.pushOrderCompleted(event);
    }

    @RabbitListener(queues = "${saga.rabbit.queues.orderCancelled}")
    public void onOrderCancelled(OrderCancelledEvent event) {
        log.info("[NOTIF] OrderCancelled: orderId={}, reason={}", event.orderId(), event.reason());
        mailService.sendOrderCancelled(event);
        wsService.pushOrderCancelled(event);
    }
}
