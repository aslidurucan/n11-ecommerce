package com.n11bootcamp.order.service.payment;

import com.n11bootcamp.order.dto.CardRequest;
import com.n11bootcamp.order.entity.Order;

public interface PaymentProcessor {

    void process(Order order, CardRequest card);
}
