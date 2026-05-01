package com.pms.order.infra.rabbitmq;

public interface ConsumerMetrics {

    void recordSuccess(ConsumerSimulator.EventType eventType, long elapsedNanos);

    void recordFailure(ConsumerSimulator.EventType eventType, long elapsedNanos);
}
