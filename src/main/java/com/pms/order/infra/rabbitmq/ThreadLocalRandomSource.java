package com.pms.order.infra.rabbitmq;

import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

@Component
public class ThreadLocalRandomSource implements RandomSource {

    @Override
    public long nextLong(long minInclusive, long maxInclusive) {
        if (minInclusive >= maxInclusive) {
            return minInclusive;
        }
        return ThreadLocalRandom.current().nextLong(minInclusive, maxInclusive + 1);
    }

    @Override
    public double nextDouble() {
        return ThreadLocalRandom.current().nextDouble();
    }
}
