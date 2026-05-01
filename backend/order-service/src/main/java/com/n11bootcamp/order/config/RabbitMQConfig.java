package com.n11bootcamp.order.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${saga.rabbit.exchange}") private String exchange;
    @Value("${saga.rabbit.dlx}")      private String dlxName;
    @Value("${saga.rabbit.dlq}")      private String dlqName;

    @Value("${saga.rabbit.queues.stockReserved}")  private String stockReservedQueue;
    @Value("${saga.rabbit.queues.stockRejected}")  private String stockRejectedQueue;
    @Value("${saga.rabbit.routingKeys.stockReserved}")  private String stockReservedRk;
    @Value("${saga.rabbit.routingKeys.stockRejected}")  private String stockRejectedRk;

    // ===== EXCHANGE =====
    @Bean public TopicExchange sagaExchange() { return new TopicExchange(exchange, true, false); }

    // ===== DLX/DLQ =====
    @Bean public TopicExchange sagaDlx() { return new TopicExchange(dlxName, true, false); }

    @Bean public Queue sagaDlq() { return QueueBuilder.durable(dlqName).build(); }

    @Bean public Binding dlqBinding() {
        return BindingBuilder.bind(sagaDlq()).to(sagaDlx()).with("#");
    }

    // ===== STOCK RESERVED =====
    @Bean
    public Queue stockReservedQ() {
        return QueueBuilder.durable(stockReservedQueue)
            .deadLetterExchange(dlxName)
            .deadLetterRoutingKey(stockReservedRk + ".dead")
            .build();
    }

    @Bean
    public Binding stockReservedBinding() {
        return BindingBuilder.bind(stockReservedQ()).to(sagaExchange()).with(stockReservedRk);
    }

    // ===== STOCK REJECTED =====
    @Bean
    public Queue stockRejectedQ() {
        return QueueBuilder.durable(stockRejectedQueue)
            .deadLetterExchange(dlxName)
            .deadLetterRoutingKey(stockRejectedRk + ".dead")
            .build();
    }

    @Bean
    public Binding stockRejectedBinding() {
        return BindingBuilder.bind(stockRejectedQ()).to(sagaExchange()).with(stockRejectedRk);
    }

    // ===== JSON CONVERTER =====
    @Bean
    public MessageConverter messageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        converter.setAlwaysConvertToInferredType(true);
        return converter;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}
