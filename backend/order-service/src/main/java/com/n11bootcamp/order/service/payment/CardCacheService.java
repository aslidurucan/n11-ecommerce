package com.n11bootcamp.order.service.payment;

import com.n11bootcamp.order.dto.CardRequest;

public interface CardCacheService {

    void storeCard(Long orderId, CardRequest card);

    CardRequest getCard(Long orderId);

    void deleteCard(Long orderId);
}
