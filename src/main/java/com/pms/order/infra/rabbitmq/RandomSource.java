package com.pms.order.infra.rabbitmq;

public interface RandomSource {

    long nextLong(long minInclusive, long maxInclusive);

    double nextDouble();
}
