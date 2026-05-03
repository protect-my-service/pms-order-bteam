package com.pms.order.infra.rabbitmq;

import com.pms.order.event.OrderCancelledEvent;
import com.pms.order.event.OrderCreatedEvent;
import com.pms.order.event.OrderPaidEvent;
import com.pms.order.global.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitMQEventConsumer {

    private final ConsumerSimulator simulator;
    private final ConsumerMetrics metrics;

    @RabbitListener(queues = RabbitMQConfig.ORDER_CREATED_QUEUE)
    public void consumeOrderCreated(OrderCreatedEvent event) {
        long startedAt = System.nanoTime();
        try {
            simulator.simulate(ConsumerSimulator.EventType.ORDER_CREATED);
            metrics.recordSuccess(ConsumerSimulator.EventType.ORDER_CREATED, System.nanoTime() - startedAt);
            log.info("Consumed OrderCreatedEvent: eventId={} orderId={}", event.getEventId(), event.getData().getOrderId());
        } catch (RuntimeException e) {
            metrics.recordFailure(ConsumerSimulator.EventType.ORDER_CREATED, System.nanoTime() - startedAt);
            log.warn("Failed to consume OrderCreatedEvent: eventId={} reason={}", event.getEventId(), e.getMessage());
            throw e;
        }
    }

    @RabbitListener(queues = RabbitMQConfig.ORDER_PAID_QUEUE)
    public void consumeOrderPaid(OrderPaidEvent event) {
        long startedAt = System.nanoTime();
        try {
            simulator.simulate(ConsumerSimulator.EventType.ORDER_PAID);
            metrics.recordSuccess(ConsumerSimulator.EventType.ORDER_PAID, System.nanoTime() - startedAt);
            log.info("Consumed OrderPaidEvent: eventId={} orderId={}", event.getEventId(), event.getData().getOrderId());
        } catch (RuntimeException e) {
            metrics.recordFailure(ConsumerSimulator.EventType.ORDER_PAID, System.nanoTime() - startedAt);
            log.warn("Failed to consume OrderPaidEvent: eventId={} reason={}", event.getEventId(), e.getMessage());
            throw e;
        }
    }

    @RabbitListener(queues = RabbitMQConfig.ORDER_CANCELLED_QUEUE)
    public void consumeOrderCancelled(OrderCancelledEvent event) {
        long startedAt = System.nanoTime();
        try {
            simulator.simulate(ConsumerSimulator.EventType.ORDER_CANCELLED);
            metrics.recordSuccess(ConsumerSimulator.EventType.ORDER_CANCELLED, System.nanoTime() - startedAt);
            log.info("Consumed OrderCancelledEvent: eventId={} orderId={}", event.getEventId(), event.getData().getOrderId());
        } catch (RuntimeException e) {
            metrics.recordFailure(ConsumerSimulator.EventType.ORDER_CANCELLED, System.nanoTime() - startedAt);
            log.warn("Failed to consume OrderCancelledEvent: eventId={} reason={}", event.getEventId(), e.getMessage());
            throw e;
        }
    }
}
