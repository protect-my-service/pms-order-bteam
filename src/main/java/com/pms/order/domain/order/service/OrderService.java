package com.pms.order.domain.order.service;

import com.pms.order.domain.cart.entity.Cart;
import com.pms.order.domain.cart.entity.CartItem;
import com.pms.order.domain.cart.repository.CartItemRepository;
import com.pms.order.domain.cart.repository.CartRepository;
import com.pms.order.domain.member.entity.Member;
import com.pms.order.domain.member.repository.MemberRepository;
import com.pms.order.domain.order.dto.*;
import com.pms.order.domain.order.entity.Order;
import com.pms.order.domain.order.entity.OrderItem;
import com.pms.order.domain.order.entity.OrderStatus;
import com.pms.order.domain.order.entity.OrderNumberSequence;
import com.pms.order.domain.order.repository.OrderNumberSequenceRepository;
import com.pms.order.domain.order.repository.OrderRepository;
import com.pms.order.domain.payment.entity.Payment;
import com.pms.order.domain.payment.entity.PaymentStatus;
import com.pms.order.domain.payment.repository.PaymentRepository;
import com.pms.order.domain.product.entity.Product;
import com.pms.order.domain.product.repository.ProductRepository;
import com.pms.order.event.OrderCancelledEvent;
import com.pms.order.event.OrderCreatedEvent;
import com.pms.order.global.config.OrderCancelPolicyProperties;
import com.pms.order.global.exception.BusinessException;
import com.pms.order.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderNumberSequenceRepository orderNumberSequenceRepository;
    private final MemberRepository memberRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final PaymentRepository paymentRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final EntityManager entityManager;
    private final OrderCancelPolicyProperties cancelPolicy;
    private final Clock clock;

    @Transactional
    public OrderResponse createOrder(Long memberId, CreateOrderRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        Cart cart = cartRepository.findByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_NOT_FOUND));

        List<CartItem> cartItems = cartItemRepository.findAllByIdInAndCartId(request.getCartItemIds(), cart.getId());

        if (cartItems.isEmpty()) {
            throw new BusinessException(ErrorCode.CART_EMPTY);
        }

        if (cartItems.size() != request.getCartItemIds().size()) {
            throw new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND, "일부 장바구니 상품을 찾을 수 없습니다.");
        }

        String orderNumber = generateOrderNumber();

        BigDecimal totalAmount = BigDecimal.ZERO;
        Order order = Order.builder()
                .orderNumber(orderNumber)
                .member(member)
                .totalAmount(BigDecimal.ZERO)
                .build();
        orderRepository.save(order);

        for (CartItem cartItem : cartItems) {
            Product product = productRepository.findByIdWithLock(cartItem.getProduct().getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
            entityManager.refresh(product);

            if (!product.isAvailable()) {
                throw new BusinessException(ErrorCode.PRODUCT_NOT_AVAILABLE,
                        "상품이 판매 불가 상태입니다: " + product.getName());
            }

            product.deductStock(cartItem.getQuantity());

            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .product(product)
                    .productName(product.getName())
                    .productPrice(product.getPrice())
                    .quantity(cartItem.getQuantity())
                    .build();

            order.addItem(orderItem);
            totalAmount = totalAmount.add(product.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity())));
        }

        order.updateTotalAmount(totalAmount);
        cartItemRepository.deleteAll(cartItems);

        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .data(OrderCreatedEvent.OrderCreatedData.builder()
                        .orderId(order.getId())
                        .orderNumber(order.getOrderNumber())
                        .memberId(memberId)
                        .totalAmount(totalAmount)
                        .itemCount(cartItems.size())
                        .items(order.getItems().stream().map(item -> Map.<String, Object>of(
                                "productId", item.getProduct().getId(),
                                "productName", item.getProductName(),
                                "quantity", item.getQuantity(),
                                "price", item.getProductPrice()
                        )).toList())
                        .build())
                .build();
        applicationEventPublisher.publishEvent(event);

        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long memberId, Long orderId) {
        Order order = orderRepository.findByIdAndMemberId(orderId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        return paymentRepository.findByOrderId(orderId)
                .map(payment -> OrderResponse.from(order, payment))
                .orElseGet(() -> OrderResponse.from(order));
    }

    @Transactional(readOnly = true)
    public Page<OrderListResponse> getOrders(Long memberId, Pageable pageable) {
        return orderRepository.findByMemberId(memberId, pageable).map(OrderListResponse::from);
    }

    @Transactional
    public OrderCancelResponse cancelOrder(Long memberId, Long orderId, CancelOrderRequest request) {
        CancelOrderRequest req = request == null ? new CancelOrderRequest() : request;

        if (req.isEmptyItems()) {
            throw new BusinessException(ErrorCode.INVALID_CANCEL_REQUEST, "items 가 비어 있습니다.");
        }

        boolean isPartial = req.hasItems();

        if (isPartial && !cancelPolicy.isAllowPartial()) {
            throw new BusinessException(ErrorCode.PARTIAL_CANCEL_DISABLED);
        }
        if (isPartial && req.getItems().size() > cancelPolicy.getMaxItemsPerRequest()) {
            throw new BusinessException(ErrorCode.INVALID_CANCEL_REQUEST,
                    "items 수가 허용 한도(" + cancelPolicy.getMaxItemsPerRequest() + ")를 초과했습니다.");
        }

        Order order = orderRepository.findByIdAndMemberIdForUpdate(orderId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        Payment payment = switch (order.getStatus()) {
            case PAID, PARTIALLY_CANCELLED -> paymentRepository.findByOrderId(orderId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
            default -> null;
        };

        order.ensureCancellableAt(LocalDateTime.now(clock),
                payment == null ? null : payment.getPaidAt(),
                cancelPolicy.window(),
                isPartial);

        List<TargetItem> targets = isPartial
                ? resolvePartialTargets(order, req.getItems())
                : resolveFullTargets(order);

        // 상품 락 획득 순서는 입력 순서 그대로 유지 (의도된 공격 표면 — 데드락 유도 가능)
        BigDecimal refundAmount = BigDecimal.ZERO;
        List<OrderCancelResponse.CancelledItem> cancelledItems = new ArrayList<>();

        for (TargetItem t : targets) {
            Product product = productRepository.findByIdWithLock(t.orderItem.getProduct().getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
            product.restoreStock(t.cancelQty);
            t.orderItem.cancelQuantity(t.cancelQty);

            BigDecimal unitPrice = t.orderItem.getProductPrice();
            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(t.cancelQty));
            refundAmount = refundAmount.add(subtotal);

            cancelledItems.add(OrderCancelResponse.CancelledItem.builder()
                    .orderItemId(t.orderItem.getId())
                    .productId(product.getId())
                    .cancelledQuantity(t.cancelQty)
                    .unitPrice(unitPrice)
                    .subtotal(subtotal)
                    .build());
        }

        if (payment != null && refundAmount.signum() > 0 && payment.getStatus() == PaymentStatus.APPROVED) {
            payment.partialCancel(refundAmount);
        }

        boolean allCancelled = order.getItems().stream().allMatch(OrderItem::isFullyCancelled);
        OrderStatus newStatus = allCancelled
                ? OrderStatus.CANCELLED
                : (isPartial ? OrderStatus.PARTIALLY_CANCELLED : OrderStatus.CANCELLED);
        order.changeStatus(newStatus);

        OrderCancelledEvent event = OrderCancelledEvent.builder()
                .data(OrderCancelledEvent.OrderCancelledData.builder()
                        .orderId(order.getId())
                        .orderNumber(order.getOrderNumber())
                        .reason(req.getReason() != null ? req.getReason() : "사용자 취소")
                        .refundAmount(refundAmount)
                        .build())
                .build();
        applicationEventPublisher.publishEvent(event);

        return OrderCancelResponse.from(order,
                isPartial && !allCancelled ? "PARTIAL" : "FULL",
                refundAmount,
                cancelledItems,
                LocalDateTime.now(clock));
    }

    private List<TargetItem> resolveFullTargets(Order order) {
        List<TargetItem> list = new ArrayList<>();
        for (OrderItem item : order.getItems()) {
            int remaining = item.getActiveQuantity();
            if (remaining > 0) {
                list.add(new TargetItem(item, remaining));
            }
        }
        if (list.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS, "취소 가능한 상품이 없습니다.");
        }
        return list;
    }

    private List<TargetItem> resolvePartialTargets(Order order, List<CancelOrderRequest.CancelItem> requests) {
        Map<Long, OrderItem> byId = new LinkedHashMap<>();
        for (OrderItem item : order.getItems()) {
            byId.put(item.getId(), item);
        }

        Set<Long> seen = new HashSet<>();
        List<TargetItem> list = new ArrayList<>();
        for (CancelOrderRequest.CancelItem r : requests) {
            if (!seen.add(r.getOrderItemId())) {
                throw new BusinessException(ErrorCode.INVALID_CANCEL_REQUEST,
                        "items 에 중복된 orderItemId 가 있습니다.");
            }
            OrderItem item = byId.get(r.getOrderItemId());
            if (item == null) {
                throw new BusinessException(ErrorCode.ORDER_ITEM_NOT_IN_ORDER);
            }
            if (r.getQuantity() <= 0) {
                throw new BusinessException(ErrorCode.INVALID_CANCEL_QUANTITY);
            }
            if (item.getCancelledQuantity() + r.getQuantity() > item.getQuantity()) {
                throw new BusinessException(ErrorCode.CANCEL_QUANTITY_EXCEEDS_REMAINING);
            }
            list.add(new TargetItem(item, r.getQuantity()));
        }
        return list;
    }

    private String generateOrderNumber() {
        String datePrefix = "ORD-" + LocalDate.now(clock).format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-";
        long seq = orderNumberSequenceRepository.save(new OrderNumberSequence()).getId();
        return datePrefix + String.format("%06d", seq);
    }

    private record TargetItem(OrderItem orderItem, int cancelQty) {
    }
}
