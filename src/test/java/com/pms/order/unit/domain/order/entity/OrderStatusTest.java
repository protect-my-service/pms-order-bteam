package com.pms.order.unit.domain.order.entity;

import com.pms.order.domain.order.entity.OrderStatus;
import com.pms.order.global.exception.BusinessException;
import com.pms.order.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("OrderStatus 상태 전이 검증")
class OrderStatusTest {

    @Nested
    @DisplayName("PENDING 상태에서")
    class FromPending {

        @Test
        @DisplayName("PAID로 전이할 수 있다")
        void should_transition_to_paid() {
            assertThatNoException()
                    .isThrownBy(() -> OrderStatus.PENDING.validateTransitionTo(OrderStatus.PAID));
        }

        @Test
        @DisplayName("CANCELLED로 전이할 수 있다")
        void should_transition_to_cancelled() {
            assertThatNoException()
                    .isThrownBy(() -> OrderStatus.PENDING.validateTransitionTo(OrderStatus.CANCELLED));
        }

        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, names = {"PREPARING", "SHIPPING", "DELIVERED", "PARTIALLY_CANCELLED", "REFUND_REQUESTED", "REFUNDED", "RETURNED"})
        @DisplayName("허용되지 않은 상태로는 전이할 수 없다")
        void should_reject_invalid_transition(OrderStatus invalidTarget) {
            assertThatThrownBy(() -> OrderStatus.PENDING.validateTransitionTo(invalidTarget))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assert bex.getErrorCode() == ErrorCode.INVALID_ORDER_STATUS;
                    });
        }
    }

    @Nested
    @DisplayName("PAID 상태에서")
    class FromPaid {

        @Test
        @DisplayName("PREPARING으로 전이할 수 있다")
        void should_transition_to_preparing() {
            assertThatNoException()
                    .isThrownBy(() -> OrderStatus.PAID.validateTransitionTo(OrderStatus.PREPARING));
        }

        @Test
        @DisplayName("REFUND_REQUESTED로 전이할 수 있다")
        void should_transition_to_refund_requested() {
            assertThatNoException()
                    .isThrownBy(() -> OrderStatus.PAID.validateTransitionTo(OrderStatus.REFUND_REQUESTED));
        }

        @Test
        @DisplayName("CANCELLED로 전이할 수 있다 (주문 취소)")
        void should_transition_to_cancelled() {
            assertThatNoException()
                    .isThrownBy(() -> OrderStatus.PAID.validateTransitionTo(OrderStatus.CANCELLED));
        }

        @Test
        @DisplayName("PARTIALLY_CANCELLED로 전이할 수 있다 (부분 취소)")
        void should_transition_to_partially_cancelled() {
            assertThatNoException()
                    .isThrownBy(() -> OrderStatus.PAID.validateTransitionTo(OrderStatus.PARTIALLY_CANCELLED));
        }
    }

    @Nested
    @DisplayName("PARTIALLY_CANCELLED 상태에서")
    class FromPartiallyCancelled {

        @Test
        @DisplayName("PARTIALLY_CANCELLED로 다시 전이할 수 있다 (추가 부분 취소)")
        void should_transition_to_partially_cancelled_again() {
            assertThatNoException()
                    .isThrownBy(() -> OrderStatus.PARTIALLY_CANCELLED.validateTransitionTo(OrderStatus.PARTIALLY_CANCELLED));
        }

        @Test
        @DisplayName("CANCELLED로 전이할 수 있다 (남은 전체 취소)")
        void should_transition_to_cancelled() {
            assertThatNoException()
                    .isThrownBy(() -> OrderStatus.PARTIALLY_CANCELLED.validateTransitionTo(OrderStatus.CANCELLED));
        }

        @Test
        @DisplayName("PREPARING으로 전이할 수 있다 (남은 항목 배송 준비)")
        void should_transition_to_preparing() {
            assertThatNoException()
                    .isThrownBy(() -> OrderStatus.PARTIALLY_CANCELLED.validateTransitionTo(OrderStatus.PREPARING));
        }

        @Test
        @DisplayName("REFUND_REQUESTED로 전이할 수 있다 (남은 금액 환불 요청)")
        void should_transition_to_refund_requested() {
            assertThatNoException()
                    .isThrownBy(() -> OrderStatus.PARTIALLY_CANCELLED.validateTransitionTo(OrderStatus.REFUND_REQUESTED));
        }

        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, names = {"PENDING", "PAID", "SHIPPING", "DELIVERED", "REFUNDED", "RETURNED"})
        @DisplayName("허용되지 않은 상태로는 전이할 수 없다")
        void should_reject_invalid_transition(OrderStatus invalidTarget) {
            assertThatThrownBy(() -> OrderStatus.PARTIALLY_CANCELLED.validateTransitionTo(invalidTarget))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("배송 흐름")
    class ShippingFlow {

        @Test
        @DisplayName("PREPARING에서 SHIPPING으로 전이할 수 있다")
        void preparing_to_shipping() {
            assertThatNoException()
                    .isThrownBy(() -> OrderStatus.PREPARING.validateTransitionTo(OrderStatus.SHIPPING));
        }

        @Test
        @DisplayName("SHIPPING에서 DELIVERED로 전이할 수 있다")
        void shipping_to_delivered() {
            assertThatNoException()
                    .isThrownBy(() -> OrderStatus.SHIPPING.validateTransitionTo(OrderStatus.DELIVERED));
        }

        @Test
        @DisplayName("DELIVERED에서 RETURNED로 전이할 수 있다")
        void delivered_to_returned() {
            assertThatNoException()
                    .isThrownBy(() -> OrderStatus.DELIVERED.validateTransitionTo(OrderStatus.RETURNED));
        }
    }

    @Nested
    @DisplayName("최종 상태에서")
    class TerminalStates {

        @ParameterizedTest
        @EnumSource(OrderStatus.class)
        @DisplayName("CANCELLED에서는 어떤 상태로도 전이할 수 없다")
        void cancelled_is_terminal(OrderStatus target) {
            assertThatThrownBy(() -> OrderStatus.CANCELLED.validateTransitionTo(target))
                    .isInstanceOf(BusinessException.class);
        }

        @ParameterizedTest
        @EnumSource(OrderStatus.class)
        @DisplayName("REFUNDED에서는 어떤 상태로도 전이할 수 없다")
        void refunded_is_terminal(OrderStatus target) {
            assertThatThrownBy(() -> OrderStatus.REFUNDED.validateTransitionTo(target))
                    .isInstanceOf(BusinessException.class);
        }

        @ParameterizedTest
        @EnumSource(OrderStatus.class)
        @DisplayName("RETURNED에서는 어떤 상태로도 전이할 수 없다")
        void returned_is_terminal(OrderStatus target) {
            assertThatThrownBy(() -> OrderStatus.RETURNED.validateTransitionTo(target))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("환불 흐름")
    class RefundFlow {

        @Test
        @DisplayName("REFUND_REQUESTED에서 REFUNDED로 전이할 수 있다")
        void refund_requested_to_refunded() {
            assertThatNoException()
                    .isThrownBy(() -> OrderStatus.REFUND_REQUESTED.validateTransitionTo(OrderStatus.REFUNDED));
        }
    }
}
