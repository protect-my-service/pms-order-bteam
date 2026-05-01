package com.pms.order.infra.rabbitmq;

public interface ConsumerSimulator {

    void simulate(EventType eventType);

    enum EventType {
        ORDER_CREATED,
        ORDER_PAID,
        ORDER_CANCELLED
    }
}
