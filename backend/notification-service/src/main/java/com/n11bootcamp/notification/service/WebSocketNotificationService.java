package com.n11bootcamp.notification.service;

import com.n11bootcamp.notification.event.OrderCancelledEvent;
import com.n11bootcamp.notification.event.OrderCompletedEvent;

public interface WebSocketNotificationService {

    void pushOrderCompleted(OrderCompletedEvent event);

    void pushOrderCancelled(OrderCancelledEvent event);
}
