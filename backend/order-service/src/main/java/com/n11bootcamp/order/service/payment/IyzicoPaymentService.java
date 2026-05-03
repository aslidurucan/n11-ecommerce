package com.n11bootcamp.order.service.payment;

import com.n11bootcamp.order.dto.CardRequest;
import com.n11bootcamp.order.entity.Order;

public interface IyzicoPaymentService {

    PaymentResult charge(Order order, CardRequest card);

    record PaymentResult(boolean success, String paymentId, String errorMessage) {

        public static PaymentResult success(String paymentId) {
            return new PaymentResult(true, paymentId, null);
        }

        public static PaymentResult failure(String errorMessage) {
            return new PaymentResult(false, null, errorMessage);
        }
    }
}
