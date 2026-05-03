package com.n11bootcamp.order.service;


public interface OutboxEventService {


    void persist(String aggregateType, String aggregateId,
                 String eventType, String routingKey, Object payload);
}
