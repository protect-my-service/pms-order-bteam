package com.pms.order.domain.payment.entity;

import com.pms.order.domain.order.entity.Order;
import com.pms.order.global.common.BaseTimeEntity;
import com.pms.order.global.exception.BusinessException;
import com.pms.order.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "payment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @Column(name = "payment_key", nullable = false, unique = true, length = 100)
    private String paymentKey;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "cancelled_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal cancelledAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentStatus status;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Builder
    public Payment(Order order, String paymentKey, BigDecimal amount) {
        this.order = order;
        this.paymentKey = paymentKey;
        this.amount = amount;
        this.cancelledAmount = BigDecimal.ZERO;
        this.status = PaymentStatus.READY;
    }

    public void approve(LocalDateTime paidAt) {
        this.status = PaymentStatus.APPROVED;
        this.paidAt = paidAt;
    }

    public void fail() {
        this.status = PaymentStatus.FAILED;
    }

    public void cancel() {
        if (this.status != PaymentStatus.APPROVED) {
            this.status = PaymentStatus.CANCELLED;
            return;
        }
        BigDecimal remaining = this.amount.subtract(this.cancelledAmount);
        if (remaining.signum() > 0) {
            partialCancel(remaining);
        } else {
            this.status = PaymentStatus.CANCELLED;
        }
    }

    public void partialCancel(BigDecimal cancelAmount) {
        Objects.requireNonNull(cancelAmount);
        if (cancelAmount.signum() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_CANCEL_QUANTITY);
        }
        if (this.status != PaymentStatus.APPROVED) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS,
                    "승인된 결제만 취소할 수 있습니다.");
        }
        BigDecimal next = this.cancelledAmount.add(cancelAmount);
        if (next.compareTo(this.amount) > 0) {
            throw new BusinessException(ErrorCode.REFUND_EXCEEDS_PAYMENT);
        }
        this.cancelledAmount = next;
        if (next.compareTo(this.amount) == 0) {
            this.status = PaymentStatus.CANCELLED;
        }
    }
}
