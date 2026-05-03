package com.n11bootcamp.stock.config;

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

    @Value("${saga.rabbit.queues.stockReserveRequested}") private String stockReserveRequestedQueue;
    @Value("${saga.rabbit.queues.paymentFailed}")         private String paymentFailedQueue;
    @Value("${saga.rabbit.queues.orderCompleted}")        private String orderCompletedQueue;

    @Value("${saga.rabbit.routingKeys.stockReserveRequested}") private String stockReserveRequestedRk;
    @Value("${saga.rabbit.routingKeys.paymentFailed}")         private String paymentFailedRk;
    @Value("${saga.rabbit.routingKeys.orderCompleted}")        private String orderCompletedRk;

    @Bean
    public TopicExchange sagaExchange() {
        return new TopicExchange(exchange, true, false);
    }

    @Bean
    public TopicExchange sagaDlx() {
        return new TopicExchange(dlxName, true, false);
    }

    @Bean
    public Queue sagaDlq() {
        return QueueBuilder.durable(dlqName).build();
    }

    @Bean
    public Binding dlqBinding() {
        return BindingBuilder.bind(sagaDlq()).to(sagaDlx()).with("#");
    }

    @Bean
    public Queue stockReserveRequestedQ() {
        return QueueBuilder.durable(stockReserveRequestedQueue)
                .deadLetterExchange(dlxName)
                .deadLetterRoutingKey(stockReserveRequestedRk + ".dead")
                .build();
    }

    @Bean
    public Binding stockReserveRequestedBinding() {
        return BindingBuilder.bind(stockReserveRequestedQ())
                .to(sagaExchange())
                .with(stockReserveRequestedRk);
    }

    @Bean
    public Queue paymentFailedQ() {
        return QueueBuilder.durable(paymentFailedQueue)
                .deadLetterExchange(dlxName)
                .deadLetterRoutingKey(paymentFailedRk + ".dead")
                .build();
    }

    @Bean
    public Binding paymentFailedBinding() {
        return BindingBuilder.bind(paymentFailedQ())
                .to(sagaExchange())
                .with(paymentFailedRk);
    }

    @Bean
    public Queue orderCompletedQ() {
        return QueueBuilder.durable(orderCompletedQueue)
                .deadLetterExchange(dlxName)
                .deadLetterRoutingKey(orderCompletedRk + ".dead")
                .build();
    }

    @Bean
    public Binding orderCompletedBinding() {
        return BindingBuilder.bind(orderCompletedQ())
                .to(sagaExchange())
                .with(orderCompletedRk);
    }

    @Bean
    public MessageConverter messageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        converter.setAlwaysConvertToInferredType(true);
        return converter;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}