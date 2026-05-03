package com.pms.order.unit.infra.rabbitmq;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.pms.order.event.OrderCancelledEvent;
import com.pms.order.event.OrderCreatedEvent;
import com.pms.order.event.OrderPaidEvent;
import com.pms.order.infra.rabbitmq.ConsumerMetrics;
import com.pms.order.infra.rabbitmq.ConsumerSimulator;
import com.pms.order.infra.rabbitmq.ConsumerSimulator.EventType;
import com.pms.order.infra.rabbitmq.RabbitMQEventConsumer;
import com.pms.order.infra.rabbitmq.SimulatedDownstreamException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RabbitMQEventConsumerTest {

    @Mock
    private ConsumerSimulator simulator;

    @Mock
    private ConsumerMetrics metrics;

    @InjectMocks
    private RabbitMQEventConsumer consumer;

    private OrderCreatedEvent createdEvent;
    private OrderPaidEvent paidEvent;
    private OrderCancelledEvent cancelledEvent;

    @BeforeEach
    void setUp() {
        createdEvent = OrderCreatedEvent.builder()
                .data(OrderCreatedEvent.OrderCreatedData.builder()
                        .orderId(1L)
                        .orderNumber("ORD-1")
                        .memberId(10L)
                        .totalAmount(new BigDecimal("12000"))
                        .itemCount(2)
                        .items(java.util.List.of())
                        .build())
                .build();

        paidEvent = OrderPaidEvent.builder()
                .data(OrderPaidEvent.OrderPaidData.builder()
                        .orderId(2L)
                        .orderNumber("ORD-2")
                        .paymentKey("pk-1")
                        .amount(new BigDecimal("9000"))
                        .paidAt(LocalDateTime.now())
                        .build())
                .build();

        cancelledEvent = OrderCancelledEvent.builder()
                .data(OrderCancelledEvent.OrderCancelledData.builder()
                        .orderId(3L)
                        .orderNumber("ORD-3")
                        .reason("user-request")
                        .refundAmount(new BigDecimal("5000"))
                        .build())
                .build();
    }

    @Test
    @DisplayName("정상 흐름: simulator 통과 시 metrics.recordSuccess 호출 + 예외 없음 (created)")
    void consumeOrderCreated_success() {
        assertThatCode(() -> consumer.consumeOrderCreated(createdEvent))
                .doesNotThrowAnyException();

        verify(simulator).simulate(EventType.ORDER_CREATED);
        verify(metrics).recordSuccess(eq(EventType.ORDER_CREATED), anyLong());
        verify(metrics, never()).recordFailure(eq(EventType.ORDER_CREATED), anyLong());
    }

    @Test
    @DisplayName("정상 흐름: paid")
    void consumeOrderPaid_success() {
        assertThatCode(() -> consumer.consumeOrderPaid(paidEvent))
                .doesNotThrowAnyException();

        verify(simulator).simulate(EventType.ORDER_PAID);
        verify(metrics).recordSuccess(eq(EventType.ORDER_PAID), anyLong());
    }

    @Test
    @DisplayName("정상 흐름: cancelled")
    void consumeOrderCancelled_success() {
        assertThatCode(() -> consumer.consumeOrderCancelled(cancelledEvent))
                .doesNotThrowAnyException();

        verify(simulator).simulate(EventType.ORDER_CANCELLED);
        verify(metrics).recordSuccess(eq(EventType.ORDER_CANCELLED), anyLong());
    }

    @Test
    @DisplayName("실패 흐름: simulator 가 throw 하면 recordFailure 호출 후 예외를 그대로 전파한다 (created)")
    void consumeOrderCreated_failure_propagates() {
        willThrow(new SimulatedDownstreamException("nope"))
                .given(simulator).simulate(EventType.ORDER_CREATED);

        assertThatThrownBy(() -> consumer.consumeOrderCreated(createdEvent))
                .isInstanceOf(SimulatedDownstreamException.class);

        verify(metrics).recordFailure(eq(EventType.ORDER_CREATED), anyLong());
        verify(metrics, never()).recordSuccess(eq(EventType.ORDER_CREATED), anyLong());
    }

    @Test
    @DisplayName("실패 흐름: paid 도 동일하게 예외 전파 + recordFailure")
    void consumeOrderPaid_failure_propagates() {
        willThrow(new SimulatedDownstreamException("nope"))
                .given(simulator).simulate(EventType.ORDER_PAID);

        assertThatThrownBy(() -> consumer.consumeOrderPaid(paidEvent))
                .isInstanceOf(SimulatedDownstreamException.class);

        verify(metrics).recordFailure(eq(EventType.ORDER_PAID), anyLong());
    }

    @Test
    @DisplayName("실패 흐름: cancelled 도 동일하게 예외 전파 + recordFailure")
    void consumeOrderCancelled_failure_propagates() {
        willThrow(new SimulatedDownstreamException("nope"))
                .given(simulator).simulate(EventType.ORDER_CANCELLED);

        assertThatThrownBy(() -> consumer.consumeOrderCancelled(cancelledEvent))
                .isInstanceOf(SimulatedDownstreamException.class);

        verify(metrics).recordFailure(eq(EventType.ORDER_CANCELLED), anyLong());
    }
}
