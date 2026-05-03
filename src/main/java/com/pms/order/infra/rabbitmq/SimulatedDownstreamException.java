package com.pms.order.infra.rabbitmq;

public class SimulatedDownstreamException extends RuntimeException {

    public SimulatedDownstreamException(String message) {
        super(message);
    }
}
