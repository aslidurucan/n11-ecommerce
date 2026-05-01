package com.n11bootcamp.notification.listener;

import com.n11bootcamp.notification.event.OrderCancelledEvent;
import com.n11bootcamp.notification.event.OrderCompletedEvent;
import com.n11bootcamp.notification.service.MailService;
import com.n11bootcamp.notification.service.WebSocketNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Notification event listener.
 *
 * <p><b>Kanal izolasyonu:</b> Mail ve WebSocket bağımsız iki kanal. Birinin
 * hatası diğerini engellememeli — her biri ayrı try/catch ile sarılı.</p>
 *
 * <p><b>Hata stratejisi:</b></p>
 * <ul>
 *   <li><b>Mail hatası:</b> Bubble up — listener fail eder, RabbitMQ retry/DLQ
 *       devreye girer (önemli, mail kaybolmamalı).</li>
 *   <li><b>WebSocket hatası:</b> Log + skip — WS broadcast best-effort,
 *       kullanıcı zaten online değilse zaten alamayacaktı.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {

    private final MailService mailService;
    private final WebSocketNotificationService wsService;

    @RabbitListener(queues = "${saga.rabbit.queues.orderCompleted}")
    public void onOrderCompleted(OrderCompletedEvent event) {
        log.info("[NOTIF] OrderCompleted: orderId={}, eventId={}", event.orderId(), event.eventId());

        // WebSocket — best effort, hata bubble up etmez
        tryPushWebSocket(() -> wsService.pushOrderCompleted(event), event.orderId(), "COMPLETED");

        // Mail — kritik, hata bubble up eder (DLQ'ya gönderir)
        mailService.sendOrderCompleted(event);
    }

    @RabbitListener(queues = "${saga.rabbit.queues.orderCancelled}")
    public void onOrderCancelled(OrderCancelledEvent event) {
        log.info("[NOTIF] OrderCancelled: orderId={}, reason={}", event.orderId(), event.reason());

        tryPushWebSocket(() -> wsService.pushOrderCancelled(event), event.orderId(), "CANCELLED");

        mailService.sendOrderCancelled(event);
    }

    /**
     * WebSocket push best-effort. Hata olursa log + devam et.
     * Niye? WS subscriber yoksa zaten kayıp; mail kritik kanal.
     */
    private void tryPushWebSocket(Runnable pushAction, Long orderId, String status) {
        try {
            pushAction.run();
        } catch (Exception e) {
            log.error("[NOTIF] WebSocket push failed for orderId={}, status={}: {}",
                orderId, status, e.getMessage(), e);
            // Bubble up etmiyoruz — WS broadcast best-effort
        }
    }
}
