package com.pms.order.infra.rabbitmq;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class MicrometerConsumerMetrics implements ConsumerMetrics {

    static final String PROCESSED_COUNTER = "mq.consumer.processed";
    static final String DURATION_TIMER = "mq.consumer.duration";

    private final Map<ConsumerSimulator.EventType, Counter> successCounters = new EnumMap<>(ConsumerSimulator.EventType.class);
    private final Map<ConsumerSimulator.EventType, Counter> failureCounters = new EnumMap<>(ConsumerSimulator.EventType.class);
    private final Map<ConsumerSimulator.EventType, Timer> timers = new EnumMap<>(ConsumerSimulator.EventType.class);

    public MicrometerConsumerMetrics(MeterRegistry registry) {
        for (ConsumerSimulator.EventType type : ConsumerSimulator.EventType.values()) {
            String tag = tagFor(type);
            successCounters.put(type, Counter.builder(PROCESSED_COUNTER)
                    .tag("event", tag)
                    .tag("result", "success")
                    .register(registry));
            failureCounters.put(type, Counter.builder(PROCESSED_COUNTER)
                    .tag("event", tag)
                    .tag("result", "failure")
                    .register(registry));
            timers.put(type, Timer.builder(DURATION_TIMER)
                    .tag("event", tag)
                    .register(registry));
        }
    }

    @Override
    public void recordSuccess(ConsumerSimulator.EventType eventType, long elapsedNanos) {
        successCounters.get(eventType).increment();
        timers.get(eventType).record(elapsedNanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public void recordFailure(ConsumerSimulator.EventType eventType, long elapsedNanos) {
        failureCounters.get(eventType).increment();
        timers.get(eventType).record(elapsedNanos, TimeUnit.NANOSECONDS);
    }

    static String tagFor(ConsumerSimulator.EventType eventType) {
        return switch (eventType) {
            case ORDER_CREATED -> "created";
            case ORDER_PAID -> "paid";
            case ORDER_CANCELLED -> "cancelled";
        };
    }
}
