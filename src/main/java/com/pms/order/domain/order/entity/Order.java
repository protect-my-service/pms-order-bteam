package com.pms.order.domain.order.entity;

import com.pms.order.domain.member.entity.Member;
import com.pms.order.global.common.BaseTimeEntity;
import com.pms.order.global.exception.BusinessException;
import com.pms.order.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", nullable = false, unique = true, length = 50)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "ordered_at", nullable = false)
    private LocalDateTime orderedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    private List<OrderItem> items = new ArrayList<>();

    @Builder
    public Order(String orderNumber, Member member, BigDecimal totalAmount) {
        this.orderNumber = orderNumber;
        this.member = member;
        this.status = OrderStatus.PENDING;
        this.totalAmount = totalAmount;
        this.orderedAt = LocalDateTime.now();
    }

    public void changeStatus(OrderStatus nextStatus) {
        this.status.validateTransitionTo(nextStatus);
        this.status = nextStatus;
    }

    public void addItem(OrderItem item) {
        this.items.add(item);
    }

    public void updateTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public void ensureCancellableAt(LocalDateTime now, LocalDateTime paidAt,
                                    Duration window, boolean hasItems) {
        if (this.status == OrderStatus.PENDING) {
            if (hasItems) {
                throw new BusinessException(ErrorCode.INVALID_CANCEL_REQUEST,
                        "PENDING 주문은 부분 취소할 수 없습니다.");
            }
            return;
        }
        if (this.status != OrderStatus.PAID && this.status != OrderStatus.PARTIALLY_CANCELLED) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS,
                    "현재 상태에서는 취소할 수 없습니다. 환불 요청을 이용하세요.");
        }
        if (paidAt == null) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_FOUND);
        }
        if (now.isAfter(paidAt.plus(window))) {
            throw new BusinessException(ErrorCode.CANCEL_WINDOW_EXPIRED,
                    "결제 후 " + window.toHours() + "시간이 지나 취소할 수 없습니다.");
        }
    }
}
