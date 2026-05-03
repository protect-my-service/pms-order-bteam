package com.pms.order.infra.rabbitmq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultConsumerSimulator implements ConsumerSimulator {

    private final SimulationProperties properties;
    private final RandomSource randomSource;
    private final Sleeper sleeper;

    @Override
    public void simulate(EventType eventType) {
        if (!properties.isEnabled()) {
            return;
        }

        SimulationProperties.DelayRange range = properties.delayFor(eventType);
        long delayMs = randomSource.nextLong(range.getMinMs(), range.getMaxMs());

        try {
            sleeper.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SimulatedDownstreamException("Consumer interrupted while simulating processing for " + eventType);
        }

        if (randomSource.nextDouble() < properties.getFailureRate()) {
            throw new SimulatedDownstreamException("Simulated downstream failure for " + eventType);
        }
    }
}
