package com.pms.order.infra.rabbitmq;

public interface Sleeper {

    void sleep(long millis) throws InterruptedException;
}
