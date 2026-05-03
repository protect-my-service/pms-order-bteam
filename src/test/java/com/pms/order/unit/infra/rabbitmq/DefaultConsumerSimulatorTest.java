package com.pms.order.unit.infra.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.pms.order.infra.rabbitmq.ConsumerSimulator;
import com.pms.order.infra.rabbitmq.DefaultConsumerSimulator;
import com.pms.order.infra.rabbitmq.RandomSource;
import com.pms.order.infra.rabbitmq.SimulatedDownstreamException;
import com.pms.order.infra.rabbitmq.SimulationProperties;
import com.pms.order.infra.rabbitmq.Sleeper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultConsumerSimulatorTest {

    @Mock
    private RandomSource randomSource;

    @Mock
    private Sleeper sleeper;

    private SimulationProperties properties;

    @InjectMocks
    private DefaultConsumerSimulator simulator;

    @BeforeEach
    void setUp() {
        properties = new SimulationProperties();
        simulator = new DefaultConsumerSimulator(properties, randomSource, sleeper);
    }

    @Test
    @DisplayName("enabled=false 면 sleep/random 둘 다 호출하지 않고 즉시 반환한다")
    void disabled_isShortCircuited() throws InterruptedException {
        properties.setEnabled(false);

        simulator.simulate(ConsumerSimulator.EventType.ORDER_CREATED);

        verify(sleeper, never()).sleep(anyLong());
        verify(randomSource, never()).nextDouble();
        verify(randomSource, never()).nextLong(anyLong(), anyLong());
    }

    @Test
    @DisplayName("처리시간 범위 — properties 의 min/max 그대로 RandomSource 에 전달되고 그 값으로 sleep 한다")
    void delayRange_isFedFromProperties() throws InterruptedException {
        properties.getDelay().getOrderPaid().setMinMs(100);
        properties.getDelay().getOrderPaid().setMaxMs(300);
        given(randomSource.nextLong(100L, 300L)).willReturn(187L);
        given(randomSource.nextDouble()).willReturn(0.999d);

        simulator.simulate(ConsumerSimulator.EventType.ORDER_PAID);

        verify(randomSource).nextLong(100L, 300L);
        verify(sleeper, times(1)).sleep(187L);
    }

    @Test
    @DisplayName("이벤트 타입별로 서로 다른 delay range 가 적용된다")
    void delayRange_perEventType() throws InterruptedException {
        given(randomSource.nextLong(50L, 150L)).willReturn(80L);
        given(randomSource.nextLong(50L, 200L)).willReturn(120L);
        given(randomSource.nextDouble()).willReturn(0.5d);

        simulator.simulate(ConsumerSimulator.EventType.ORDER_CREATED);
        simulator.simulate(ConsumerSimulator.EventType.ORDER_CANCELLED);

        verify(randomSource).nextLong(50L, 150L);
        verify(randomSource).nextLong(50L, 200L);
        verify(sleeper).sleep(80L);
        verify(sleeper).sleep(120L);
    }

    @Test
    @DisplayName("failureRate=0 + random 어떤 값이든 예외가 발생하지 않는다")
    void failureRateZero_neverThrows() {
        properties.setFailureRate(0.0d);
        given(randomSource.nextLong(anyLong(), anyLong())).willReturn(10L);
        given(randomSource.nextDouble()).willReturn(0.0001d);

        assertThatCode(() -> simulator.simulate(ConsumerSimulator.EventType.ORDER_CREATED))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("failureRate=1 이면 임의 random 값에서도 항상 SimulatedDownstreamException 을 던진다")
    void failureRateOne_alwaysThrows() {
        properties.setFailureRate(1.0d);
        given(randomSource.nextLong(anyLong(), anyLong())).willReturn(10L);
        given(randomSource.nextDouble()).willReturn(0.999d);

        assertThatThrownBy(() -> simulator.simulate(ConsumerSimulator.EventType.ORDER_PAID))
                .isInstanceOf(SimulatedDownstreamException.class)
                .hasMessageContaining("ORDER_PAID");
    }

    @Test
    @DisplayName("Sleeper 가 InterruptedException 을 던지면 interrupt flag 를 보존한 뒤 SimulatedDownstreamException 으로 감싼다")
    void interruptedSleep_setsFlagAndWraps() throws InterruptedException {
        given(randomSource.nextLong(anyLong(), anyLong())).willReturn(10L);
        org.mockito.BDDMockito.willThrow(new InterruptedException("boom")).given(sleeper).sleep(anyLong());

        try {
            assertThatThrownBy(() -> simulator.simulate(ConsumerSimulator.EventType.ORDER_CANCELLED))
                    .isInstanceOf(SimulatedDownstreamException.class);

            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            // clear flag so we do not pollute other tests in the same thread
            Thread.interrupted();
        }
    }
}
