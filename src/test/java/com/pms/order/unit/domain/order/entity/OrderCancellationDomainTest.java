package com.pms.order.unit.domain.order.entity;

import com.pms.order.domain.member.entity.Member;
import com.pms.order.domain.order.entity.Order;
import com.pms.order.domain.order.entity.OrderItem;
import com.pms.order.domain.order.entity.OrderStatus;
import com.pms.order.domain.product.entity.Product;
import com.pms.order.domain.product.entity.ProductStatus;
import com.pms.order.global.exception.BusinessException;
import com.pms.order.global.exception.ErrorCode;
import com.pms.order.support.TestFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("주문 취소 도메인 로직")
class OrderCancellationDomainTest {

    private final Member member = TestFixture.member(1L, "a@a.com", "u");
    private final Product product = TestFixture.product(10L, "p", BigDecimal.valueOf(10000), 100, ProductStatus.ON_SALE);

    @Nested
    @DisplayName("Order.ensureCancellableAt")
    class EnsureCancellableAt {

        @Test
        @DisplayName("PENDING + items 없음 → 시간 검증 없이 통과")
        void pending_without_items_is_ok() {
            Order order = TestFixture.order(1L, "ORD", member, OrderStatus.PENDING, BigDecimal.valueOf(10000));
            assertThatNoException().isThrownBy(() ->
                    order.ensureCancellableAt(LocalDateTime.now(), null, Duration.ofHours(1), false));
        }

        @Test
        @DisplayName("PENDING + items → INVALID_CANCEL_REQUEST")
        void pending_with_items_rejected() {
            Order order = TestFixture.order(1L, "ORD", member, OrderStatus.PENDING, BigDecimal.valueOf(10000));
            assertThatThrownBy(() ->
                    order.ensureCancellableAt(LocalDateTime.now(), null, Duration.ofHours(1), true))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_CANCEL_REQUEST));
        }

        @Test
        @DisplayName("PAID + 경계 시각(=paidAt+window) → 허용")
        void paid_at_boundary_allowed() {
            Order order = TestFixture.order(1L, "ORD", member, OrderStatus.PAID, BigDecimal.valueOf(10000));
            LocalDateTime paidAt = LocalDateTime.parse("2026-04-19T09:00:00");
            LocalDateTime now = paidAt.plusHours(1);
            assertThatNoException().isThrownBy(() ->
                    order.ensureCancellableAt(now, paidAt, Duration.ofHours(1), false));
        }

        @Test
        @DisplayName("PAID + 경계 1나노 초과 → CANCEL_WINDOW_EXPIRED")
        void paid_past_boundary_rejected() {
            Order order = TestFixture.order(1L, "ORD", member, OrderStatus.PAID, BigDecimal.valueOf(10000));
            LocalDateTime paidAt = LocalDateTime.parse("2026-04-19T09:00:00");
            LocalDateTime now = paidAt.plusHours(1).plusNanos(1);
            assertThatThrownBy(() ->
                    order.ensureCancellableAt(now, paidAt, Duration.ofHours(1), false))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.CANCEL_WINDOW_EXPIRED));
        }

        @Test
        @DisplayName("PAID + paidAt null → PAYMENT_NOT_FOUND")
        void paid_missing_paid_at_raises_payment_not_found() {
            Order order = TestFixture.order(1L, "ORD", member, OrderStatus.PAID, BigDecimal.valueOf(10000));
            assertThatThrownBy(() ->
                    order.ensureCancellableAt(LocalDateTime.now(), null, Duration.ofHours(1), false))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.PAYMENT_NOT_FOUND));
        }

        @Test
        @DisplayName("PREPARING → INVALID_ORDER_STATUS")
        void preparing_rejected() {
            Order order = TestFixture.order(1L, "ORD", member, OrderStatus.PREPARING, BigDecimal.valueOf(10000));
            assertThatThrownBy(() ->
                    order.ensureCancellableAt(LocalDateTime.now(), LocalDateTime.now(), Duration.ofHours(1), false))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_ORDER_STATUS));
        }
    }

    @Nested
    @DisplayName("OrderItem.cancelQuantity")
    class CancelQuantity {

        @Test
        @DisplayName("정상 수량 차감")
        void normal_cancel() {
            Order order = TestFixture.order(1L, "ORD", member, OrderStatus.PAID, BigDecimal.valueOf(10000));
            OrderItem item = TestFixture.orderItem(100L, order, product, 3);

            item.cancelQuantity(1);
            assertThat(item.getCancelledQuantity()).isEqualTo(1);
            assertThat(item.getActiveQuantity()).isEqualTo(2);
            assertThat(item.isFullyCancelled()).isFalse();

            item.cancelQuantity(2);
            assertThat(item.isFullyCancelled()).isTrue();
        }

        @Test
        @DisplayName("0 이하 수량 → INVALID_CANCEL_QUANTITY")
        void zero_or_negative_rejected() {
            Order order = TestFixture.order(1L, "ORD", member, OrderStatus.PAID, BigDecimal.valueOf(10000));
            OrderItem item = TestFixture.orderItem(100L, order, product, 3);

            assertThatThrownBy(() -> item.cancelQuantity(0))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_CANCEL_QUANTITY));
            assertThatThrownBy(() -> item.cancelQuantity(-1))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_CANCEL_QUANTITY));
        }

        @Test
        @DisplayName("남은 수량 초과 → CANCEL_QUANTITY_EXCEEDS_REMAINING")
        void exceeding_rejected() {
            Order order = TestFixture.order(1L, "ORD", member, OrderStatus.PAID, BigDecimal.valueOf(10000));
            OrderItem item = TestFixture.orderItem(100L, order, product, 3);

            assertThatThrownBy(() -> item.cancelQuantity(4))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.CANCEL_QUANTITY_EXCEEDS_REMAINING));
        }

        @Test
        @DisplayName("누적 초과 → CANCEL_QUANTITY_EXCEEDS_REMAINING")
        void cumulative_exceeds_rejected() {
            Order order = TestFixture.order(1L, "ORD", member, OrderStatus.PAID, BigDecimal.valueOf(10000));
            OrderItem item = TestFixture.orderItem(100L, order, product, 3);

            item.cancelQuantity(2);
            assertThatThrownBy(() -> item.cancelQuantity(2))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.CANCEL_QUANTITY_EXCEEDS_REMAINING));
        }
    }
}
