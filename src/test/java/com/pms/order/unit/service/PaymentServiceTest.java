package com.pms.order.unit.service;

import com.pms.order.domain.member.entity.Member;
import com.pms.order.domain.order.entity.Order;
import com.pms.order.domain.order.entity.OrderItem;
import com.pms.order.domain.order.entity.OrderStatus;
import com.pms.order.domain.order.repository.OrderRepository;
import com.pms.order.domain.payment.client.ExternalPaymentClient;
import com.pms.order.domain.payment.entity.Payment;
import com.pms.order.domain.payment.repository.PaymentRepository;
import com.pms.order.domain.payment.service.PaymentService;
import com.pms.order.domain.product.entity.Product;
import com.pms.order.domain.product.entity.ProductStatus;
import com.pms.order.domain.product.repository.ProductRepository;
import com.pms.order.event.OrderCancelledEvent;
import com.pms.order.global.exception.BusinessException;
import com.pms.order.global.exception.ErrorCode;
import com.pms.order.support.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService 단위 테스트")
class PaymentServiceTest {

    @InjectMocks
    private PaymentService paymentService;

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private ExternalPaymentClient externalPaymentClient;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-04-19T10:00:00Z"), ZoneOffset.UTC);

    private Member member;
    private Product product;

    @BeforeEach
    void setUp() {
        member = TestFixture.member(1L, "user@test.com", "테스트유저");
        product = TestFixture.product(10L, "상품A", BigDecimal.valueOf(29900), 100, ProductStatus.ON_SALE);
    }

    @Nested
    @DisplayName("결제 요청")
    class RequestPayment {

        @Test
        @DisplayName("PENDING 상태의 주문에 대해 결제가 성공하면 PAID로 전이된다")
        void should_approve_payment_and_transition_to_paid() {
            Order order = TestFixture.order(1L, "ORD-20250401-000001", member, OrderStatus.PENDING, BigDecimal.valueOf(29900));

            given(orderRepository.findByIdAndMemberId(1L, 1L)).willReturn(Optional.of(order));
            given(paymentRepository.findByOrderIdAndStatusNot(eq(1L), any())).willReturn(Optional.empty());
            given(externalPaymentClient.requestPayment(any(), any()))
                    .willReturn(Map.of("paymentKey", "PAY-test-1234", "amount", BigDecimal.valueOf(29900), "status", "APPROVED"));
            given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> invocation.getArgument(0));

            com.pms.order.domain.payment.dto.PaymentResponse result = paymentService.requestPayment(1L, 1L);

            assertThat(result.getPaymentKey()).isEqualTo("PAY-test-1234");
            assertThat(result.getStatus()).isEqualTo("APPROVED");
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
            var eventCaptor = org.mockito.ArgumentCaptor.forClass(Object.class);
            verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue()).isInstanceOf(com.pms.order.event.OrderPaidEvent.class);
            assertThat(((com.pms.order.event.OrderPaidEvent) eventCaptor.getValue()).getData().getPaymentKey())
                    .isEqualTo("PAY-test-1234");
        }

        @Test
        @DisplayName("PENDING이 아닌 주문에 결제를 시도하면 예외가 발생한다")
        void should_throw_when_order_not_pending() {
            Order order = TestFixture.order(1L, "ORD-20250401-000001", member, OrderStatus.PAID, BigDecimal.valueOf(29900));

            given(orderRepository.findByIdAndMemberId(1L, 1L)).willReturn(Optional.of(order));

            assertThatThrownBy(() -> paymentService.requestPayment(1L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_ORDER_STATUS));
        }

        @Test
        @DisplayName("이미 결제가 진행된 주문에 중복 결제를 시도하면 예외가 발생한다")
        void should_throw_when_duplicate_payment() {
            Order order = TestFixture.order(1L, "ORD-20250401-000001", member, OrderStatus.PENDING, BigDecimal.valueOf(29900));
            Payment existingPayment = TestFixture.payment(1L, order, "PAY-existing", BigDecimal.valueOf(29900));

            given(orderRepository.findByIdAndMemberId(1L, 1L)).willReturn(Optional.of(order));
            given(paymentRepository.findByOrderIdAndStatusNot(eq(1L), any())).willReturn(Optional.of(existingPayment));

            assertThatThrownBy(() -> paymentService.requestPayment(1L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.DUPLICATE_REQUEST));
        }

        @Test
        @DisplayName("외부 결제 실패 시 주문 상태가 CANCELLED로 전이된다")
        void should_cancel_order_when_payment_fails() {
            Order order = TestFixture.order(1L, "ORD-20260409-000001", member, OrderStatus.PENDING, BigDecimal.valueOf(29900));
            OrderItem orderItem = TestFixture.orderItem(1L, order, product, 2);
            order.addItem(orderItem);

            given(orderRepository.findByIdAndMemberId(1L, 1L)).willReturn(Optional.of(order));
            given(paymentRepository.findByOrderIdAndStatusNot(eq(1L), any())).willReturn(Optional.empty());
            given(externalPaymentClient.requestPayment(any(), any()))
                    .willThrow(new BusinessException(ErrorCode.PAYMENT_FAILED));
            given(productRepository.findByIdWithLock(10L)).willReturn(Optional.of(product));
            given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> invocation.getArgument(0));

            assertThatThrownBy(() -> paymentService.requestPayment(1L, 1L))
                    .isInstanceOf(BusinessException.class);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("외부 결제 실패 시 OrderCancelledEvent가 발행된다")
        void should_publish_order_cancelled_event_when_payment_fails() {
            Order order = TestFixture.order(1L, "ORD-20260409-000001", member, OrderStatus.PENDING, BigDecimal.valueOf(29900));
            OrderItem orderItem = TestFixture.orderItem(1L, order, product, 1);
            order.addItem(orderItem);

            given(orderRepository.findByIdAndMemberId(1L, 1L)).willReturn(Optional.of(order));
            given(paymentRepository.findByOrderIdAndStatusNot(eq(1L), any())).willReturn(Optional.empty());
            given(externalPaymentClient.requestPayment(any(), any()))
                    .willThrow(new BusinessException(ErrorCode.PAYMENT_FAILED));
            given(productRepository.findByIdWithLock(10L)).willReturn(Optional.of(product));
            given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> invocation.getArgument(0));

            assertThatThrownBy(() -> paymentService.requestPayment(1L, 1L))
                    .isInstanceOf(BusinessException.class);

            var eventCaptor = org.mockito.ArgumentCaptor.forClass(Object.class);
            verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue()).isInstanceOf(OrderCancelledEvent.class);
            OrderCancelledEvent event = (OrderCancelledEvent) eventCaptor.getValue();
            assertThat(event.getData().getOrderId()).isEqualTo(1L);
            assertThat(event.getData().getOrderNumber()).isEqualTo("ORD-20260409-000001");
            assertThat(event.getData().getReason()).isEqualTo("결제 실패로 인한 자동 취소");
        }

        @Test
        @DisplayName("외부 결제 실패 시 재고가 복원되고 실패 결제 기록이 생성된다")
        void should_restore_stock_and_record_failure_when_payment_fails() {
            Order order = TestFixture.order(1L, "ORD-20250401-000001", member, OrderStatus.PENDING, BigDecimal.valueOf(29900));
            OrderItem orderItem = TestFixture.orderItem(1L, order, product, 2);
            order.addItem(orderItem);

            int stockBefore = product.getStockQuantity();

            given(orderRepository.findByIdAndMemberId(1L, 1L)).willReturn(Optional.of(order));
            given(paymentRepository.findByOrderIdAndStatusNot(eq(1L), any())).willReturn(Optional.empty());
            given(externalPaymentClient.requestPayment(any(), any()))
                    .willThrow(new BusinessException(ErrorCode.PAYMENT_FAILED));
            given(productRepository.findByIdWithLock(10L)).willReturn(Optional.of(product));
            given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> invocation.getArgument(0));

            assertThatThrownBy(() -> paymentService.requestPayment(1L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.PAYMENT_FAILED));

            assertThat(product.getStockQuantity()).isEqualTo(stockBefore + 2);
            verify(paymentRepository).save(argThat(payment -> payment.getStatus().name().equals("FAILED")));
        }
    }

    @Nested
    @DisplayName("결제 취소")
    class CancelPayment {

        @Test
        @DisplayName("결제를 취소하면 환불 처리되고 재고가 복원된다")
        void should_refund_and_restore_stock() {
            Order order = TestFixture.order(1L, "ORD-20250401-000001", member, OrderStatus.PAID, BigDecimal.valueOf(29900));
            OrderItem orderItem = TestFixture.orderItem(1L, order, product, 1);
            order.addItem(orderItem);
            Payment payment = TestFixture.payment(1L, order, "PAY-test-key", BigDecimal.valueOf(29900));
            payment.approve(LocalDateTime.ofInstant(Instant.parse("2026-04-19T09:30:00Z"), ZoneOffset.UTC));

            int stockBefore = product.getStockQuantity();

            given(paymentRepository.findByPaymentKey("PAY-test-key")).willReturn(Optional.of(payment));
            given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.of(order));
            given(productRepository.findByIdWithLock(10L)).willReturn(Optional.of(product));
            doNothing().when(externalPaymentClient).cancelPayment("PAY-test-key");

            com.pms.order.domain.payment.dto.PaymentCancelResponse result = paymentService.cancelPayment(1L, "PAY-test-key", "단순 변심");

            assertThat(result.getStatus()).isEqualTo("CANCELLED");
            assertThat(product.getStockQuantity()).isEqualTo(stockBefore + 1);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        }

        @Test
        @DisplayName("다른 회원의 결제를 취소하려 하면 예외가 발생한다")
        void should_throw_when_different_member_tries_to_cancel() {
            Order order = TestFixture.order(1L, "ORD-20250401-000001", member, OrderStatus.PAID, BigDecimal.valueOf(29900));
            Payment payment = TestFixture.payment(1L, order, "PAY-test-key", BigDecimal.valueOf(29900));

            given(paymentRepository.findByPaymentKey("PAY-test-key")).willReturn(Optional.of(payment));
            given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.of(order));

            assertThatThrownBy(() -> paymentService.cancelPayment(999L, "PAY-test-key", "단순 변심"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.ORDER_NOT_FOUND));
        }
    }
}
