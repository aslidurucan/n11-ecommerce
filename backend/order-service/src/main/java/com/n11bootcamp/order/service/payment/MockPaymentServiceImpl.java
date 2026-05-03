package com.n11bootcamp.order.service.payment;

import com.n11bootcamp.order.dto.CardRequest;
import com.n11bootcamp.order.entity.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@ConditionalOnProperty(name = "payment.mock", havingValue = "true")
@Slf4j
public class MockPaymentServiceImpl implements IyzicoPaymentService {

    @Override
    public PaymentResult charge(Order order, CardRequest card) {
        String mockPaymentId = "MOCK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("[MOCK PAYMENT] Order {} approved — paymentId={}", order.getId(), mockPaymentId);
        return PaymentResult.success(mockPaymentId);
    }
}
