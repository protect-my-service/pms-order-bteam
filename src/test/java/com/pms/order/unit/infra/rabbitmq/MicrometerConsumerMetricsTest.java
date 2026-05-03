package com.pms.order.unit.infra.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;

import com.pms.order.infra.rabbitmq.ConsumerSimulator.EventType;
import com.pms.order.infra.rabbitmq.MicrometerConsumerMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MicrometerConsumerMetricsTest {

    private SimpleMeterRegistry registry;
    private MicrometerConsumerMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new MicrometerConsumerMetrics(registry);
    }

    @Test
    @DisplayName("recordSuccess: counter(success) 1 증가 + timer 에 elapsed 누적")
    void recordSuccess() {
        metrics.recordSuccess(EventType.ORDER_CREATED, TimeUnit.MILLISECONDS.toNanos(120));

        Counter successCounter = registry.find("mq.consumer.processed")
                .tag("event", "created")
                .tag("result", "success")
                .counter();
        Timer timer = registry.find("mq.consumer.duration").tag("event", "created").timer();

        assertThat(successCounter).isNotNull();
        assertThat(successCounter.count()).isEqualTo(1.0d);
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1L);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(120.0d);
    }

    @Test
    @DisplayName("recordFailure: counter(failure) 1 증가 + timer 에 elapsed 누적")
    void recordFailure() {
        metrics.recordFailure(EventType.ORDER_PAID, TimeUnit.MILLISECONDS.toNanos(250));

        Counter failureCounter = registry.find("mq.consumer.processed")
                .tag("event", "paid")
                .tag("result", "failure")
                .counter();
        Timer timer = registry.find("mq.consumer.duration").tag("event", "paid").timer();

        assertThat(failureCounter).isNotNull();
        assertThat(failureCounter.count()).isEqualTo(1.0d);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(250.0d);
    }

    @Test
    @DisplayName("이벤트 타입별 counter 가 분리되어 집계된다")
    void countersAreSeparatedPerEventType() {
        metrics.recordSuccess(EventType.ORDER_CREATED, 1_000L);
        metrics.recordSuccess(EventType.ORDER_CREATED, 1_000L);
        metrics.recordSuccess(EventType.ORDER_PAID, 1_000L);
        metrics.recordFailure(EventType.ORDER_CANCELLED, 1_000L);

        assertThat(registry.find("mq.consumer.processed")
                .tag("event", "created").tag("result", "success").counter().count()).isEqualTo(2.0d);
        assertThat(registry.find("mq.consumer.processed")
                .tag("event", "paid").tag("result", "success").counter().count()).isEqualTo(1.0d);
        assertThat(registry.find("mq.consumer.processed")
                .tag("event", "cancelled").tag("result", "failure").counter().count()).isEqualTo(1.0d);
    }
}
