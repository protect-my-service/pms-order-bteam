package com.pms.order.infra.rabbitmq;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "consumer.simulation")
public class SimulationProperties {

    private boolean enabled = true;
    private double failureRate = 0.005d;
    private Delay delay = new Delay();

    @Getter
    @Setter
    public static class Delay {
        private DelayRange orderCreated = new DelayRange(50, 150);
        private DelayRange orderPaid = new DelayRange(100, 300);
        private DelayRange orderCancelled = new DelayRange(50, 200);
    }

    @Getter
    @Setter
    public static class DelayRange {
        private long minMs;
        private long maxMs;

        public DelayRange() {
        }

        public DelayRange(long minMs, long maxMs) {
            this.minMs = minMs;
            this.maxMs = maxMs;
        }
    }

    public DelayRange delayFor(ConsumerSimulator.EventType eventType) {
        return switch (eventType) {
            case ORDER_CREATED -> delay.getOrderCreated();
            case ORDER_PAID -> delay.getOrderPaid();
            case ORDER_CANCELLED -> delay.getOrderCancelled();
        };
    }
}
