package com.pms.order.domain.payment.service;

import com.pms.order.domain.order.entity.Order;
import com.pms.order.domain.order.entity.OrderStatus;
import com.pms.order.domain.order.repository.OrderRepository;
import com.pms.order.domain.payment.client.ExternalPaymentClient;
import com.pms.order.domain.payment.dto.PaymentCancelResponse;
import com.pms.order.domain.payment.dto.PaymentResponse;
import com.pms.order.domain.payment.entity.Payment;
import com.pms.order.domain.payment.entity.PaymentStatus;
import com.pms.order.domain.payment.repository.PaymentRepository;
import com.pms.order.domain.product.entity.Product;
import com.pms.order.domain.product.repository.ProductRepository;
import com.pms.order.event.OrderCancelledEvent;
import com.pms.order.event.OrderPaidEvent;
import com.pms.order.global.exception.BusinessException;
import com.pms.order.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ExternalPaymentClient externalPaymentClient;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final Clock clock;

    @Transactional
    public PaymentResponse requestPayment(Long memberId, Long orderId) {
        Order order = orderRepository.findByIdAndMemberId(orderId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS, "결제 대기 상태의 주문만 결제할 수 있습니다.");
        }

        if (paymentRepository.findByOrderIdAndStatusNot(orderId, PaymentStatus.FAILED).isPresent()) {
            throw new BusinessException(ErrorCode.DUPLICATE_REQUEST, "이미 결제가 진행된 주문입니다.");
        }

        Map<String, Object> paymentResult;
        try {
            paymentResult = externalPaymentClient.requestPayment(order.getOrderNumber(), order.getTotalAmount());
        } catch (BusinessException e) {
            for (var item : order.getItems()) {
                Product product = productRepository.findByIdWithLock(item.getProduct().getId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
                product.restoreStock(item.getQuantity());
            }

            Payment failedPayment = Payment.builder()
                    .order(order)
                    .paymentKey("FAILED-" + java.util.UUID.randomUUID().toString().substring(0, 8))
                    .amount(order.getTotalAmount())
                    .build();
            failedPayment.fail();
            paymentRepository.save(failedPayment);
            order.changeStatus(OrderStatus.CANCELLED);

            applicationEventPublisher.publishEvent(OrderCancelledEvent.builder()
                    .data(OrderCancelledEvent.OrderCancelledData.builder()
                            .orderId(order.getId())
                            .orderNumber(order.getOrderNumber())
                            .reason("결제 실패로 인한 자동 취소")
                            .refundAmount(java.math.BigDecimal.ZERO)
                            .build())
                    .build());

            throw e;
        }

        String paymentKey = paymentResult.get("paymentKey").toString();

        Payment payment = Payment.builder()
                .order(order)
                .paymentKey(paymentKey)
                .amount(order.getTotalAmount())
                .build();
        payment.approve(LocalDateTime.now(clock));
        paymentRepository.save(payment);

        order.changeStatus(OrderStatus.PAID);

        OrderPaidEvent event = OrderPaidEvent.builder()
                .data(OrderPaidEvent.OrderPaidData.builder()
                        .orderId(order.getId())
                        .orderNumber(order.getOrderNumber())
                        .paymentKey(paymentKey)
                        .amount(order.getTotalAmount())
                        .paidAt(payment.getPaidAt())
                        .build())
                .build();
        applicationEventPublisher.publishEvent(event);

        return PaymentResponse.from(payment);
    }

    @Transactional
    public PaymentCancelResponse cancelPayment(Long memberId, String paymentKey, String reason) {
        Payment payment = paymentRepository.findByPaymentKey(paymentKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        Order order = orderRepository.findByIdForUpdate(payment.getOrder().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (!order.getMember().getId().equals(memberId)) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }

        if (payment.getStatus() != PaymentStatus.APPROVED) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS, "승인된 결제만 취소할 수 있습니다.");
        }

        order.changeStatus(OrderStatus.REFUND_REQUESTED);
        externalPaymentClient.cancelPayment(paymentKey);
        payment.cancel();
        order.changeStatus(OrderStatus.REFUNDED);

        for (var item : order.getItems()) {
            int remaining = item.getActiveQuantity();
            if (remaining <= 0) continue;
            Product product = productRepository.findByIdWithLock(item.getProduct().getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
            product.restoreStock(remaining);
            item.cancelQuantity(remaining);
        }

        return PaymentCancelResponse.builder()
                .paymentKey(paymentKey)
                .status(payment.getStatus().name())
                .cancelledAt(LocalDateTime.now(clock))
                .build();
    }
}
