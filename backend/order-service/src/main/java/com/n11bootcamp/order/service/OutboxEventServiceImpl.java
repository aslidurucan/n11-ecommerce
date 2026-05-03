package com.n11bootcamp.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.n11bootcamp.order.entity.OutboxEvent;
import com.n11bootcamp.order.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxEventServiceImpl implements OutboxEventService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void persist(String aggregateType, String aggregateId,
                        String eventType, String routingKey, Object payload) {
        String json = serializePayload(eventType, payload);

        OutboxEvent event = OutboxEvent.builder()
            .aggregateType(aggregateType)
            .aggregateId(aggregateId)
            .eventType(eventType)
            .routingKey(routingKey)
            .payload(json)
            .build();

        outboxEventRepository.save(event);
        log.debug("Outbox event persisted: type={}, aggregateId={}", eventType, aggregateId);
    }


    private String serializePayload(String eventType, Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                "Failed to serialize outbox event payload: " + eventType, e);
        }
    }
}
