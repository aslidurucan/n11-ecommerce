package com.n11bootcamp.order.service;

import com.n11bootcamp.order.entity.OutboxEvent;
import com.n11bootcamp.order.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import java.util.List;


@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private static final int BATCH_SIZE = 50;
    private static final int MAX_RETRIES = 5;

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;

    @Value("${saga.rabbit.exchange}")
    private String exchange;

    @Scheduled(fixedDelayString = "${outbox.publisher.fixed-delay-ms:2000}")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishPending() {
        List<OutboxEvent> pending = outboxEventRepository
            .findUnpublishedForUpdate(MAX_RETRIES, PageRequest.of(0, BATCH_SIZE));

        if (pending.isEmpty()) return;

        log.debug("Publishing {} pending outbox events", pending.size());

        for (OutboxEvent event : pending) {
            try {
                Message message = MessageBuilder
                    .withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .build();
                rabbitTemplate.send(exchange, event.getRoutingKey(), message);
                event.markPublished();
                log.info("Outbox event published: id={}, type={}, routingKey={}",
                    event.getId(), event.getEventType(), event.getRoutingKey());
            } catch (AmqpException e) {
                event.markFailed(e.getMessage());
                log.warn("Outbox event publish failed: id={}, retryCount={}, error={}",
                    event.getId(), event.getRetryCount(), e.getMessage());
            }
        }
    }
}
