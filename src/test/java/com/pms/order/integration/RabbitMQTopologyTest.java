package com.pms.order.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.pms.order.global.config.RabbitMQConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.Map;

@SpringJUnitConfig(RabbitMQConfig.class)
@DisplayName("RabbitMQConfig — 토폴로지 선언 검증 (브로커 없이)")
class RabbitMQTopologyTest {

    @Autowired
    private TopicExchange orderExchange;

    @Autowired
    private DirectExchange orderDeadLetterExchange;

    @Autowired
    private Map<String, Queue> queues;

    @Autowired
    private Map<String, Binding> bindings;

    @Test
    @DisplayName("주 익스체인지(order.exchange) + DLX(order.dlx) 가 모두 선언된다")
    void exchangesAreDeclared() {
        assertThat(orderExchange.getName()).isEqualTo(RabbitMQConfig.ORDER_EXCHANGE);
        assertThat(orderDeadLetterExchange.getName()).isEqualTo(RabbitMQConfig.ORDER_DLX);
    }

    @Test
    @DisplayName("정상 큐 3개와 DLQ 3개가 모두 선언된다")
    void allSixQueuesAreDeclared() {
        assertThat(queues.values())
                .extracting(Queue::getName)
                .containsExactlyInAnyOrder(
                        RabbitMQConfig.ORDER_CREATED_QUEUE,
                        RabbitMQConfig.ORDER_PAID_QUEUE,
                        RabbitMQConfig.ORDER_CANCELLED_QUEUE,
                        RabbitMQConfig.ORDER_CREATED_DLQ,
                        RabbitMQConfig.ORDER_PAID_DLQ,
                        RabbitMQConfig.ORDER_CANCELLED_DLQ);
    }

    @Test
    @DisplayName("정상 큐는 dead-letter-exchange/routing-key 인자가 DLX→DLQ 로 향하도록 설정된다")
    void mainQueuesPointToDeadLetterExchange() {
        assertThat(queueNamed(RabbitMQConfig.ORDER_CREATED_QUEUE).getArguments())
                .containsEntry("x-dead-letter-exchange", RabbitMQConfig.ORDER_DLX)
                .containsEntry("x-dead-letter-routing-key", RabbitMQConfig.ORDER_CREATED_DLQ);
        assertThat(queueNamed(RabbitMQConfig.ORDER_PAID_QUEUE).getArguments())
                .containsEntry("x-dead-letter-exchange", RabbitMQConfig.ORDER_DLX)
                .containsEntry("x-dead-letter-routing-key", RabbitMQConfig.ORDER_PAID_DLQ);
        assertThat(queueNamed(RabbitMQConfig.ORDER_CANCELLED_QUEUE).getArguments())
                .containsEntry("x-dead-letter-exchange", RabbitMQConfig.ORDER_DLX)
                .containsEntry("x-dead-letter-routing-key", RabbitMQConfig.ORDER_CANCELLED_DLQ);
    }

    @Test
    @DisplayName("DLQ 자체에는 dead-letter 인자가 없다 (재라우팅 루프 방지)")
    void deadLetterQueuesHaveNoForwardingArgs() {
        assertThat(queueNamed(RabbitMQConfig.ORDER_CREATED_DLQ).getArguments())
                .doesNotContainKey("x-dead-letter-exchange");
        assertThat(queueNamed(RabbitMQConfig.ORDER_PAID_DLQ).getArguments())
                .doesNotContainKey("x-dead-letter-exchange");
        assertThat(queueNamed(RabbitMQConfig.ORDER_CANCELLED_DLQ).getArguments())
                .doesNotContainKey("x-dead-letter-exchange");
    }

    @Test
    @DisplayName("정상 큐 ↔ order.exchange / DLQ ↔ order.dlx 바인딩이 모두 있다")
    void bindingsAreCorrect() {
        assertThat(bindings.values())
                .extracting(Binding::getExchange, Binding::getDestination, Binding::getRoutingKey)
                .containsExactlyInAnyOrder(
                        tuple(RabbitMQConfig.ORDER_EXCHANGE, RabbitMQConfig.ORDER_CREATED_QUEUE, RabbitMQConfig.ORDER_CREATED_KEY),
                        tuple(RabbitMQConfig.ORDER_EXCHANGE, RabbitMQConfig.ORDER_PAID_QUEUE, RabbitMQConfig.ORDER_PAID_KEY),
                        tuple(RabbitMQConfig.ORDER_EXCHANGE, RabbitMQConfig.ORDER_CANCELLED_QUEUE, RabbitMQConfig.ORDER_CANCELLED_KEY),
                        tuple(RabbitMQConfig.ORDER_DLX, RabbitMQConfig.ORDER_CREATED_DLQ, RabbitMQConfig.ORDER_CREATED_DLQ),
                        tuple(RabbitMQConfig.ORDER_DLX, RabbitMQConfig.ORDER_PAID_DLQ, RabbitMQConfig.ORDER_PAID_DLQ),
                        tuple(RabbitMQConfig.ORDER_DLX, RabbitMQConfig.ORDER_CANCELLED_DLQ, RabbitMQConfig.ORDER_CANCELLED_DLQ));
    }

    private Queue queueNamed(String name) {
        return queues.values().stream()
                .filter(q -> q.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Queue not declared: " + name));
    }
}
