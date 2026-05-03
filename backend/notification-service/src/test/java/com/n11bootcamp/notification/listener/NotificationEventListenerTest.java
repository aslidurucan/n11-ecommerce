package com.n11bootcamp.notification.listener;

import com.n11bootcamp.notification.event.OrderCancelledEvent;
import com.n11bootcamp.notification.event.OrderCompletedEvent;
import com.n11bootcamp.notification.service.MailService;
import com.n11bootcamp.notification.service.WebSocketNotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationEventListener — mail + websocket delivery")
class NotificationEventListenerTest {

    @Mock private MailService mailService;
    @Mock private WebSocketNotificationService wsService;

    @InjectMocks
    private NotificationEventListener listener;

    @Test
    @DisplayName("OrderCompletedEvent: hem mail hem websocket çağrılır")
    void onOrderCompleted_callsBothMailAndWebSocket() {
        OrderCompletedEvent event = new OrderCompletedEvent(
                "evt-1", 100L, "user@example.com", "user-1",
                new BigDecimal("250.00"), Instant.now()
        );

        listener.onOrderCompleted(event);

        verify(mailService, times(1)).sendOrderCompleted(event);
        verify(wsService, times(1)).pushOrderCompleted(event);
    }

    @Test
    @DisplayName("OrderCancelledEvent: hem mail hem websocket çağrılır")
    void onOrderCancelled_callsBothMailAndWebSocket() {
        OrderCancelledEvent event = new OrderCancelledEvent(
                "evt-2", 200L, "user@example.com", "user-1",
                "Insufficient stock", Instant.now()
        );

        listener.onOrderCancelled(event);

        verify(mailService, times(1)).sendOrderCancelled(event);
        verify(wsService, times(1)).pushOrderCancelled(event);
    }

    @Test
    @DisplayName("Mail servisi hata fırlatırsa exception RabbitMQ'ya iletilir (DLQ retry için)")
    void onOrderCompleted_whenMailFails_exceptionPropagates() {
        OrderCompletedEvent event = new OrderCompletedEvent(
                "evt-3", 300L, "user@example.com", "user-1",
                new BigDecimal("100.00"), Instant.now()
        );

        doThrow(new RuntimeException("SMTP connection failed"))
                .when(mailService).sendOrderCompleted(any(OrderCompletedEvent.class));

        assertThatThrownBy(() -> listener.onOrderCompleted(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("SMTP connection failed");

        verify(wsService, times(1)).pushOrderCompleted(event);
        verify(mailService, times(1)).sendOrderCompleted(event);
    }

    @Test
    @DisplayName("WebSocket servisi hata fırlatırsa yutulur (best-effort), mail yine de gönderilir")
    void onOrderCancelled_whenWebSocketFails_mailStillSent() {
        OrderCancelledEvent event = new OrderCancelledEvent(
                "evt-4", 400L, "user@example.com", "user-1",
                "Payment failed", Instant.now()
        );

        doThrow(new RuntimeException("WebSocket session closed"))
                .when(wsService).pushOrderCancelled(any(OrderCancelledEvent.class));

        listener.onOrderCancelled(event);

        verify(wsService, times(1)).pushOrderCancelled(event);
        verify(mailService, times(1)).sendOrderCancelled(event);
    }
}
