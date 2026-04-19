package com.pms.order.domain.order.entity;

import com.pms.order.global.exception.BusinessException;
import com.pms.order.global.exception.ErrorCode;

import java.util.Set;

public enum OrderStatus {
    PENDING,
    PAID,
    PREPARING,
    SHIPPING,
    DELIVERED,
    CANCELLED,
    PARTIALLY_CANCELLED,
    REFUND_REQUESTED,
    REFUNDED,
    RETURNED;

    private static final java.util.Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = java.util.Map.of(
            PENDING, Set.of(PAID, CANCELLED),
            PAID, Set.of(PREPARING, REFUND_REQUESTED, CANCELLED, PARTIALLY_CANCELLED),
            PARTIALLY_CANCELLED, Set.of(PARTIALLY_CANCELLED, CANCELLED, PREPARING, REFUND_REQUESTED),
            PREPARING, Set.of(SHIPPING),
            SHIPPING, Set.of(DELIVERED),
            DELIVERED, Set.of(RETURNED),
            REFUND_REQUESTED, Set.of(REFUNDED)
    );

    public void validateTransitionTo(OrderStatus next) {
        Set<OrderStatus> allowed = TRANSITIONS.getOrDefault(this, Set.of());
        if (!allowed.contains(next)) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS,
                    "주문 상태를 " + this + "에서 " + next + "로 변경할 수 없습니다.");
        }
    }
}
