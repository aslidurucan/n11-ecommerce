package com.n11bootcamp.notification.service;

import com.n11bootcamp.notification.event.OrderCancelledEvent;
import com.n11bootcamp.notification.event.OrderCompletedEvent;

public interface MailService {

    void sendOrderCompleted(OrderCompletedEvent event);

    void sendOrderCancelled(OrderCancelledEvent event);
}
