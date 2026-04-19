package com.pms.order.unit.service;

import com.pms.order.domain.cart.entity.Cart;
import com.pms.order.domain.cart.entity.CartItem;
import com.pms.order.domain.cart.repository.CartItemRepository;
import com.pms.order.domain.cart.repository.CartRepository;
import com.pms.order.domain.member.entity.Member;
import com.pms.order.domain.member.repository.MemberRepository;
import com.pms.order.domain.order.dto.CancelOrderRequest;
import com.pms.order.domain.order.dto.CreateOrderRequest;
import com.pms.order.domain.order.dto.OrderCancelResponse;
import com.pms.order.domain.order.dto.OrderResponse;
import com.pms.order.domain.order.entity.Order;
import com.pms.order.domain.order.entity.OrderItem;
import com.pms.order.domain.order.entity.OrderNumberSequence;
import com.pms.order.domain.order.entity.OrderStatus;
import com.pms.order.domain.order.repository.OrderNumberSequenceRepository;
import com.pms.order.domain.order.repository.OrderRepository;
import com.pms.order.domain.order.service.OrderService;
import com.pms.order.domain.payment.entity.Payment;
import com.pms.order.domain.payment.repository.PaymentRepository;
import com.pms.order.domain.product.entity.Product;
import com.pms.order.domain.product.entity.ProductStatus;
import com.pms.order.domain.product.repository.ProductRepository;
import com.pms.order.event.OrderCancelledEvent;
import com.pms.order.event.OrderCreatedEvent;
import com.pms.order.global.config.OrderCancelPolicyProperties;
import com.pms.order.global.exception.BusinessException;
import com.pms.order.global.exception.ErrorCode;
import com.pms.order.support.TestFixture;
import jakarta.persistence.EntityManager;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService 단위 테스트")
class OrderServiceTest {

    @InjectMocks
    private OrderService orderService;

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderNumberSequenceRepository orderNumberSequenceRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private CartRepository cartRepository;
    @Mock
    private CartItemRepository cartItemRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;
    @Mock
    private EntityManager entityManager;

    @Spy
    private OrderCancelPolicyProperties cancelPolicy = new OrderCancelPolicyProperties();

    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-04-19T10:00:00Z"), ZoneOffset.UTC);

    private Member member;
    private Cart cart;
    private Product productA;
    private Product productB;

    @BeforeEach
    void setUp() {
        member = TestFixture.member(1L, "user@test.com", "테스트유저");
        cart = TestFixture.cart(1L, member);
        productA = TestFixture.product(10L, "상품A", BigDecimal.valueOf(29900), 100, ProductStatus.ON_SALE);
        productB = TestFixture.product(20L, "상품B", BigDecimal.valueOf(15000), 50, ProductStatus.ON_SALE);
    }

    @Nested
    @DisplayName("주문 생성")
    class CreateOrder {

        @Test
        @DisplayName("장바구니 상품으로 주문을 생성하면 재고가 차감되고 주문 응답이 반환된다")
        void should_create_order_and_deduct_stock() {
            CartItem cartItem = TestFixture.cartItem(100L, cart, productA, 2);
            CreateOrderRequest request = new CreateOrderRequest();
            ReflectionTestUtils.setField(request, "cartItemIds", List.of(100L));

            given(memberRepository.findById(1L)).willReturn(Optional.of(member));
            given(cartRepository.findByMemberId(1L)).willReturn(Optional.of(cart));
            given(cartItemRepository.findAllByIdInAndCartId(List.of(100L), 1L)).willReturn(List.of(cartItem));
            given(productRepository.findByIdWithLock(10L)).willReturn(Optional.of(productA));
            given(orderNumberSequenceRepository.save(any(OrderNumberSequence.class))).willAnswer(invocation -> {
                OrderNumberSequence seq = invocation.getArgument(0);
                ReflectionTestUtils.setField(seq, "id", 1L);
                return seq;
            });
            given(orderRepository.save(any(Order.class))).willAnswer(invocation -> {
                Order order = invocation.getArgument(0);
                ReflectionTestUtils.setField(order, "id", 1L);
                return order;
            });

            OrderResponse response = orderService.createOrder(1L, request);

            assertThat(response.getOrderNumber()).startsWith("ORD-");
            assertThat(response.getStatus()).isEqualTo("PENDING");
            assertThat(response.getItems()).hasSize(1);
            assertThat(productA.getStockQuantity()).isEqualTo(98);
            verify(cartItemRepository).deleteAll(List.of(cartItem));
            var eventCaptor = org.mockito.ArgumentCaptor.forClass(Object.class);
            verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue()).isInstanceOf(OrderCreatedEvent.class);
            assertThat(((OrderCreatedEvent) eventCaptor.getValue()).getData().getMemberId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("존재하지 않는 회원으로 주문하면 예외가 발생한다")
        void should_throw_when_member_not_found() {
            CreateOrderRequest request = new CreateOrderRequest();
            ReflectionTestUtils.setField(request, "cartItemIds", List.of(100L));
            given(memberRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.createOrder(999L, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.MEMBER_NOT_FOUND));
        }

        @Test
        @DisplayName("장바구니가 비어있으면 예외가 발생한다")
        void should_throw_when_cart_items_empty() {
            CreateOrderRequest request = new CreateOrderRequest();
            ReflectionTestUtils.setField(request, "cartItemIds", List.of(100L));

            given(memberRepository.findById(1L)).willReturn(Optional.of(member));
            given(cartRepository.findByMemberId(1L)).willReturn(Optional.of(cart));
            given(cartItemRepository.findAllByIdInAndCartId(List.of(100L), 1L)).willReturn(List.of());

            assertThatThrownBy(() -> orderService.createOrder(1L, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.CART_EMPTY));
        }

        @Test
        @DisplayName("판매 불가 상품이 포함되면 예외가 발생한다")
        void should_throw_when_product_not_available() {
            Product soldOutProduct = TestFixture.product(10L, "품절상품", BigDecimal.valueOf(10000), 0, ProductStatus.SOLD_OUT);
            CartItem cartItem = TestFixture.cartItem(100L, cart, productA, 1);
            CreateOrderRequest request = new CreateOrderRequest();
            ReflectionTestUtils.setField(request, "cartItemIds", List.of(100L));

            given(memberRepository.findById(1L)).willReturn(Optional.of(member));
            given(cartRepository.findByMemberId(1L)).willReturn(Optional.of(cart));
            given(cartItemRepository.findAllByIdInAndCartId(List.of(100L), 1L)).willReturn(List.of(cartItem));
            given(productRepository.findByIdWithLock(10L)).willReturn(Optional.of(soldOutProduct));
            given(orderNumberSequenceRepository.save(any(OrderNumberSequence.class))).willAnswer(invocation -> {
                OrderNumberSequence seq = invocation.getArgument(0);
                ReflectionTestUtils.setField(seq, "id", 1L);
                return seq;
            });
            given(orderRepository.save(any(Order.class))).willAnswer(invocation -> {
                Order order = invocation.getArgument(0);
                ReflectionTestUtils.setField(order, "id", 1L);
                return order;
            });

            assertThatThrownBy(() -> orderService.createOrder(1L, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.PRODUCT_NOT_AVAILABLE));
        }

        @Test
        @DisplayName("재고가 부족하면 예외가 발생한다")
        void should_throw_when_insufficient_stock() {
            Product lowStockProduct = TestFixture.product(10L, "재고부족", BigDecimal.valueOf(10000), 1, ProductStatus.ON_SALE);
            CartItem cartItem = TestFixture.cartItem(100L, cart, lowStockProduct, 5);
            CreateOrderRequest request = new CreateOrderRequest();
            ReflectionTestUtils.setField(request, "cartItemIds", List.of(100L));

            given(memberRepository.findById(1L)).willReturn(Optional.of(member));
            given(cartRepository.findByMemberId(1L)).willReturn(Optional.of(cart));
            given(cartItemRepository.findAllByIdInAndCartId(List.of(100L), 1L)).willReturn(List.of(cartItem));
            given(productRepository.findByIdWithLock(10L)).willReturn(Optional.of(lowStockProduct));
            given(orderNumberSequenceRepository.save(any(OrderNumberSequence.class))).willAnswer(invocation -> {
                OrderNumberSequence seq = invocation.getArgument(0);
                ReflectionTestUtils.setField(seq, "id", 1L);
                return seq;
            });
            given(orderRepository.save(any(Order.class))).willAnswer(invocation -> {
                Order order = invocation.getArgument(0);
                ReflectionTestUtils.setField(order, "id", 1L);
                return order;
            });

            assertThatThrownBy(() -> orderService.createOrder(1L, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.INSUFFICIENT_STOCK));
        }

        @Test
        @DisplayName("일부 장바구니 상품이 존재하지 않으면 예외가 발생한다")
        void should_throw_when_some_cart_items_not_found() {
            CartItem cartItem = TestFixture.cartItem(100L, cart, productA, 1);
            CreateOrderRequest request = new CreateOrderRequest();
            ReflectionTestUtils.setField(request, "cartItemIds", List.of(100L, 200L));

            given(memberRepository.findById(1L)).willReturn(Optional.of(member));
            given(cartRepository.findByMemberId(1L)).willReturn(Optional.of(cart));
            given(cartItemRepository.findAllByIdInAndCartId(List.of(100L, 200L), 1L)).willReturn(List.of(cartItem));

            assertThatThrownBy(() -> orderService.createOrder(1L, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.CART_ITEM_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("주문 취소")
    class CancelOrder {

        @Test
        @DisplayName("PENDING 상태 주문 취소 시 재고 복원 후 CANCELLED 로 전이")
        void should_restore_stock_when_cancelling_pending_order() {
            Order order = TestFixture.order(1L, "ORD-20250401-000001", member, OrderStatus.PENDING, BigDecimal.valueOf(59800));
            OrderItem orderItem = TestFixture.orderItem(1L, order, productA, 2);
            order.addItem(orderItem);

            given(orderRepository.findByIdAndMemberIdForUpdate(1L, 1L)).willReturn(Optional.of(order));
            given(productRepository.findByIdWithLock(10L)).willReturn(Optional.of(productA));

            int stockBefore = productA.getStockQuantity();

            OrderCancelResponse result = orderService.cancelOrder(1L, 1L, null);

            assertThat(result.getStatus()).isEqualTo("CANCELLED");
            assertThat(productA.getStockQuantity()).isEqualTo(stockBefore + 2);
            var eventCaptor = org.mockito.ArgumentCaptor.forClass(Object.class);
            verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue()).isInstanceOf(OrderCancelledEvent.class);
            assertThat(((OrderCancelledEvent) eventCaptor.getValue()).getData().getOrderNumber())
                    .isEqualTo("ORD-20250401-000001");
        }

        @Test
        @DisplayName("PAID 상태 주문을 윈도우 내에서 취소하면 결제도 취소된다")
        void should_cancel_payment_when_cancelling_paid_order_within_window() {
            Order order = TestFixture.order(1L, "ORD-20250401-000001", member, OrderStatus.PAID, BigDecimal.valueOf(29900));
            OrderItem orderItem = TestFixture.orderItem(1L, order, productA, 1);
            order.addItem(orderItem);
            Payment payment = TestFixture.payment(1L, order, "PAY-test-key", BigDecimal.valueOf(29900));
            payment.approve(LocalDateTime.ofInstant(Instant.parse("2026-04-19T09:30:00Z"), ZoneOffset.UTC));

            given(orderRepository.findByIdAndMemberIdForUpdate(1L, 1L)).willReturn(Optional.of(order));
            given(productRepository.findByIdWithLock(10L)).willReturn(Optional.of(productA));
            given(paymentRepository.findByOrderId(1L)).willReturn(Optional.of(payment));

            OrderCancelResponse result = orderService.cancelOrder(1L, 1L, null);

            assertThat(result.getStatus()).isEqualTo("CANCELLED");
            assertThat(payment.getStatus().name()).isEqualTo("CANCELLED");
        }

        @Test
        @DisplayName("존재하지 않는 주문을 취소하면 ORDER_NOT_FOUND")
        void should_throw_when_order_not_found() {
            given(orderRepository.findByIdAndMemberIdForUpdate(999L, 1L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.cancelOrder(1L, 999L, null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.ORDER_NOT_FOUND));
        }

        @Test
        @DisplayName("PENDING + items 요청은 INVALID_CANCEL_REQUEST")
        void should_reject_partial_cancel_on_pending() {
            Order order = TestFixture.order(1L, "ORD-20250401-000001", member, OrderStatus.PENDING, BigDecimal.valueOf(29900));
            OrderItem orderItem = TestFixture.orderItem(1L, order, productA, 2);
            order.addItem(orderItem);

            given(orderRepository.findByIdAndMemberIdForUpdate(1L, 1L)).willReturn(Optional.of(order));

            CancelOrderRequest req = CancelOrderRequest.builder()
                    .items(List.of(CancelOrderRequest.CancelItem.builder().orderItemId(1L).quantity(1).build()))
                    .build();

            assertThatThrownBy(() -> orderService.cancelOrder(1L, 1L, req))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_CANCEL_REQUEST));
        }

        @Test
        @DisplayName("items 가 빈 배열이면 INVALID_CANCEL_REQUEST")
        void should_reject_empty_items() {
            CancelOrderRequest req = CancelOrderRequest.builder()
                    .items(List.of())
                    .build();

            assertThatThrownBy(() -> orderService.cancelOrder(1L, 1L, req))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_CANCEL_REQUEST));
        }

        @Test
        @DisplayName("allow-partial=false + items 있으면 PARTIAL_CANCEL_DISABLED")
        void should_reject_when_partial_disabled() {
            cancelPolicy.setAllowPartial(false);
            CancelOrderRequest req = CancelOrderRequest.builder()
                    .items(List.of(CancelOrderRequest.CancelItem.builder().orderItemId(1L).quantity(1).build()))
                    .build();

            assertThatThrownBy(() -> orderService.cancelOrder(1L, 1L, req))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.PARTIAL_CANCEL_DISABLED));
        }

        @Test
        @DisplayName("PAID 상태에서 부분 취소 시 PARTIALLY_CANCELLED 로 전이, 환불액 누적")
        void should_partially_cancel_paid_order() {
            Order order = TestFixture.order(1L, "ORD-20250401-000001", member, OrderStatus.PAID, BigDecimal.valueOf(44900));
            OrderItem item1 = TestFixture.orderItem(1L, order, productA, 1);
            OrderItem item2 = TestFixture.orderItem(2L, order, productB, 1);
            order.addItem(item1);
            order.addItem(item2);

            Payment payment = TestFixture.payment(1L, order, "PAY-test", BigDecimal.valueOf(44900));
            payment.approve(LocalDateTime.ofInstant(Instant.parse("2026-04-19T09:30:00Z"), ZoneOffset.UTC));

            given(orderRepository.findByIdAndMemberIdForUpdate(1L, 1L)).willReturn(Optional.of(order));
            given(paymentRepository.findByOrderId(1L)).willReturn(Optional.of(payment));
            given(productRepository.findByIdWithLock(10L)).willReturn(Optional.of(productA));

            CancelOrderRequest req = CancelOrderRequest.builder()
                    .items(List.of(CancelOrderRequest.CancelItem.builder().orderItemId(1L).quantity(1).build()))
                    .build();

            OrderCancelResponse result = orderService.cancelOrder(1L, 1L, req);

            assertThat(result.getStatus()).isEqualTo("PARTIALLY_CANCELLED");
            assertThat(result.getCancelType()).isEqualTo("PARTIAL");
            assertThat(result.getRefundAmount()).isEqualByComparingTo(BigDecimal.valueOf(29900));
            assertThat(payment.getCancelledAmount()).isEqualByComparingTo(BigDecimal.valueOf(29900));
            assertThat(payment.getStatus().name()).isEqualTo("APPROVED");
        }

        @Test
        @DisplayName("다른 주문의 orderItemId 주입 시 ORDER_ITEM_NOT_IN_ORDER")
        void should_reject_foreign_order_item() {
            Order order = TestFixture.order(1L, "ORD-20250401-000001", member, OrderStatus.PAID, BigDecimal.valueOf(29900));
            OrderItem orderItem = TestFixture.orderItem(1L, order, productA, 1);
            order.addItem(orderItem);
            Payment payment = TestFixture.payment(1L, order, "PAY-test", BigDecimal.valueOf(29900));
            payment.approve(LocalDateTime.ofInstant(Instant.parse("2026-04-19T09:30:00Z"), ZoneOffset.UTC));

            given(orderRepository.findByIdAndMemberIdForUpdate(1L, 1L)).willReturn(Optional.of(order));
            given(paymentRepository.findByOrderId(1L)).willReturn(Optional.of(payment));

            CancelOrderRequest req = CancelOrderRequest.builder()
                    .items(List.of(CancelOrderRequest.CancelItem.builder().orderItemId(9999L).quantity(1).build()))
                    .build();

            assertThatThrownBy(() -> orderService.cancelOrder(1L, 1L, req))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.ORDER_ITEM_NOT_IN_ORDER));
        }

        @Test
        @DisplayName("items 에 중복된 orderItemId 가 있으면 INVALID_CANCEL_REQUEST")
        void should_reject_duplicate_order_item_id() {
            Order order = TestFixture.order(1L, "ORD-20250401-000001", member, OrderStatus.PAID, BigDecimal.valueOf(29900));
            OrderItem orderItem = TestFixture.orderItem(1L, order, productA, 3);
            order.addItem(orderItem);
            Payment payment = TestFixture.payment(1L, order, "PAY-test", BigDecimal.valueOf(29900));
            payment.approve(LocalDateTime.ofInstant(Instant.parse("2026-04-19T09:30:00Z"), ZoneOffset.UTC));

            given(orderRepository.findByIdAndMemberIdForUpdate(1L, 1L)).willReturn(Optional.of(order));
            given(paymentRepository.findByOrderId(1L)).willReturn(Optional.of(payment));

            CancelOrderRequest req = CancelOrderRequest.builder()
                    .items(List.of(
                            CancelOrderRequest.CancelItem.builder().orderItemId(1L).quantity(1).build(),
                            CancelOrderRequest.CancelItem.builder().orderItemId(1L).quantity(1).build()))
                    .build();

            assertThatThrownBy(() -> orderService.cancelOrder(1L, 1L, req))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_CANCEL_REQUEST));
        }

        @Test
        @DisplayName("남은 수량 초과 요청은 CANCEL_QUANTITY_EXCEEDS_REMAINING")
        void should_reject_exceeding_remaining() {
            Order order = TestFixture.order(1L, "ORD-20250401-000001", member, OrderStatus.PAID, BigDecimal.valueOf(29900));
            OrderItem orderItem = TestFixture.orderItem(1L, order, productA, 2);
            order.addItem(orderItem);
            Payment payment = TestFixture.payment(1L, order, "PAY-test", BigDecimal.valueOf(29900));
            payment.approve(LocalDateTime.ofInstant(Instant.parse("2026-04-19T09:30:00Z"), ZoneOffset.UTC));

            given(orderRepository.findByIdAndMemberIdForUpdate(1L, 1L)).willReturn(Optional.of(order));
            given(paymentRepository.findByOrderId(1L)).willReturn(Optional.of(payment));

            CancelOrderRequest req = CancelOrderRequest.builder()
                    .items(List.of(CancelOrderRequest.CancelItem.builder().orderItemId(1L).quantity(3).build()))
                    .build();

            assertThatThrownBy(() -> orderService.cancelOrder(1L, 1L, req))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.CANCEL_QUANTITY_EXCEEDS_REMAINING));
        }

        @Test
        @DisplayName("PAID + 윈도우 초과 취소는 CANCEL_WINDOW_EXPIRED")
        void should_reject_when_window_expired() {
            Order order = TestFixture.order(1L, "ORD-20250401-000001", member, OrderStatus.PAID, BigDecimal.valueOf(29900));
            OrderItem orderItem = TestFixture.orderItem(1L, order, productA, 1);
            order.addItem(orderItem);
            Payment payment = TestFixture.payment(1L, order, "PAY-test", BigDecimal.valueOf(29900));
            // clock=10:00, 윈도우=1h → 09:00 결제는 경계(10:00)까지 허용, 08:00 결제는 초과
            payment.approve(LocalDateTime.ofInstant(Instant.parse("2026-04-19T08:00:00Z"), ZoneOffset.UTC));

            given(orderRepository.findByIdAndMemberIdForUpdate(1L, 1L)).willReturn(Optional.of(order));
            given(paymentRepository.findByOrderId(1L)).willReturn(Optional.of(payment));

            assertThatThrownBy(() -> orderService.cancelOrder(1L, 1L, null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.CANCEL_WINDOW_EXPIRED));
        }
    }

    @Nested
    @DisplayName("주문번호 생성")
    class GenerateOrderNumber {

        @Test
        @DisplayName("주문번호는 DB에서 발급한 시퀀스 ID로 생성된다")
        void should_generate_order_number_using_db_sequence() {
            CartItem cartItem = TestFixture.cartItem(100L, cart, productA, 1);
            CreateOrderRequest request = new CreateOrderRequest();
            ReflectionTestUtils.setField(request, "cartItemIds", List.of(100L));

            given(memberRepository.findById(1L)).willReturn(Optional.of(member));
            given(cartRepository.findByMemberId(1L)).willReturn(Optional.of(cart));
            given(cartItemRepository.findAllByIdInAndCartId(List.of(100L), 1L)).willReturn(List.of(cartItem));
            given(productRepository.findByIdWithLock(10L)).willReturn(Optional.of(productA));
            given(orderNumberSequenceRepository.save(any(OrderNumberSequence.class))).willAnswer(invocation -> {
                OrderNumberSequence seq = invocation.getArgument(0);
                ReflectionTestUtils.setField(seq, "id", 42L);
                return seq;
            });
            given(orderRepository.save(any(Order.class))).willAnswer(invocation -> {
                Order order = invocation.getArgument(0);
                ReflectionTestUtils.setField(order, "id", 1L);
                return order;
            });

            OrderResponse response = orderService.createOrder(1L, request);

            assertThat(response.getOrderNumber()).endsWith("-000042");
            verify(orderNumberSequenceRepository).save(any(OrderNumberSequence.class));
        }
    }
}
