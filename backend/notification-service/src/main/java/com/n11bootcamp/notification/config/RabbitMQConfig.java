package com.n11bootcamp.notification.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${saga.rabbit.exchange}")       private String exchange;
    @Value("${saga.rabbit.dlx}")            private String dlxName;

    @Value("${saga.rabbit.queues.orderCompleted}")  private String orderCompletedQueue;
    @Value("${saga.rabbit.queues.orderCancelled}")  private String orderCancelledQueue;
    @Value("${saga.rabbit.routingKeys.orderCompleted}") private String orderCompletedRk;
    @Value("${saga.rabbit.routingKeys.orderCancelled}") private String orderCancelledRk;

    @Bean public TopicExchange sagaExchange() { return new TopicExchange(exchange, true, false); }

    @Bean public TopicExchange sagaDlx() { return new TopicExchange(dlxName, true, false); }

    @Bean
    public Queue orderCompletedQ() {
        return QueueBuilder.durable(orderCompletedQueue)
            .deadLetterExchange(dlxName)
            .deadLetterRoutingKey(orderCompletedRk + ".dead")
            .build();
    }

    @Bean
    public Binding orderCompletedBinding() {
        return BindingBuilder.bind(orderCompletedQ()).to(sagaExchange()).with(orderCompletedRk);
    }

    @Bean
    public Queue orderCancelledQ() {
        return QueueBuilder.durable(orderCancelledQueue)
            .deadLetterExchange(dlxName)
            .deadLetterRoutingKey(orderCancelledRk + ".dead")
            .build();
    }

    @Bean
    public Binding orderCancelledBinding() {
        return BindingBuilder.bind(orderCancelledQ()).to(sagaExchange()).with(orderCancelledRk);
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
