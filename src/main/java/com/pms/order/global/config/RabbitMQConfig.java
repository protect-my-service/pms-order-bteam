package com.pms.order.global.config;

import com.pms.order.infra.rabbitmq.SimulationProperties;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SimulationProperties.class)
public class RabbitMQConfig {

    public static final String ORDER_EXCHANGE = "order.exchange";
    public static final String ORDER_CREATED_QUEUE = "order.created.queue";
    public static final String ORDER_PAID_QUEUE = "order.paid.queue";
    public static final String ORDER_CANCELLED_QUEUE = "order.cancelled.queue";
    public static final String ORDER_CREATED_KEY = "order.created";
    public static final String ORDER_PAID_KEY = "order.paid";
    public static final String ORDER_CANCELLED_KEY = "order.cancelled";

    public static final String ORDER_DLX = "order.dlx";
    public static final String ORDER_CREATED_DLQ = "order.created.dlq";
    public static final String ORDER_PAID_DLQ = "order.paid.dlq";
    public static final String ORDER_CANCELLED_DLQ = "order.cancelled.dlq";

    @Bean
    public TopicExchange orderExchange() {
        return new TopicExchange(ORDER_EXCHANGE);
    }

    @Bean
    public DirectExchange orderDeadLetterExchange() {
        return new DirectExchange(ORDER_DLX);
    }

    @Bean
    public Queue orderCreatedQueue() {
        return QueueBuilder.durable(ORDER_CREATED_QUEUE)
                .withArgument("x-dead-letter-exchange", ORDER_DLX)
                .withArgument("x-dead-letter-routing-key", ORDER_CREATED_DLQ)
                .build();
    }

    @Bean
    public Queue orderPaidQueue() {
        return QueueBuilder.durable(ORDER_PAID_QUEUE)
                .withArgument("x-dead-letter-exchange", ORDER_DLX)
                .withArgument("x-dead-letter-routing-key", ORDER_PAID_DLQ)
                .build();
    }

    @Bean
    public Queue orderCancelledQueue() {
        return QueueBuilder.durable(ORDER_CANCELLED_QUEUE)
                .withArgument("x-dead-letter-exchange", ORDER_DLX)
                .withArgument("x-dead-letter-routing-key", ORDER_CANCELLED_DLQ)
                .build();
    }

    @Bean
    public Queue orderCreatedDeadLetterQueue() {
        return QueueBuilder.durable(ORDER_CREATED_DLQ).build();
    }

    @Bean
    public Queue orderPaidDeadLetterQueue() {
        return QueueBuilder.durable(ORDER_PAID_DLQ).build();
    }

    @Bean
    public Queue orderCancelledDeadLetterQueue() {
        return QueueBuilder.durable(ORDER_CANCELLED_DLQ).build();
    }

    @Bean
    public Binding orderCreatedBinding(Queue orderCreatedQueue, TopicExchange orderExchange) {
        return BindingBuilder.bind(orderCreatedQueue).to(orderExchange).with(ORDER_CREATED_KEY);
    }

    @Bean
    public Binding orderPaidBinding(Queue orderPaidQueue, TopicExchange orderExchange) {
        return BindingBuilder.bind(orderPaidQueue).to(orderExchange).with(ORDER_PAID_KEY);
    }

    @Bean
    public Binding orderCancelledBinding(Queue orderCancelledQueue, TopicExchange orderExchange) {
        return BindingBuilder.bind(orderCancelledQueue).to(orderExchange).with(ORDER_CANCELLED_KEY);
    }

    @Bean
    public Binding orderCreatedDeadLetterBinding(Queue orderCreatedDeadLetterQueue, DirectExchange orderDeadLetterExchange) {
        return BindingBuilder.bind(orderCreatedDeadLetterQueue).to(orderDeadLetterExchange).with(ORDER_CREATED_DLQ);
    }

    @Bean
    public Binding orderPaidDeadLetterBinding(Queue orderPaidDeadLetterQueue, DirectExchange orderDeadLetterExchange) {
        return BindingBuilder.bind(orderPaidDeadLetterQueue).to(orderDeadLetterExchange).with(ORDER_PAID_DLQ);
    }

    @Bean
    public Binding orderCancelledDeadLetterBinding(Queue orderCancelledDeadLetterQueue, DirectExchange orderDeadLetterExchange) {
        return BindingBuilder.bind(orderCancelledDeadLetterQueue).to(orderDeadLetterExchange).with(ORDER_CANCELLED_DLQ);
    }

    @Bean
    public MessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
