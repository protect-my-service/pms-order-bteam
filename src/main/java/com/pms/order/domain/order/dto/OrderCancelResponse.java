package com.pms.order.domain.order.dto;

import com.pms.order.domain.order.entity.Order;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCancelResponse {

    private Long orderId;
    private String orderNumber;
    private String status;
    private String cancelType;
    private BigDecimal refundAmount;
    private LocalDateTime cancelledAt;
    private List<CancelledItem> cancelledItems;

    public static OrderCancelResponse from(Order order) {
        return OrderCancelResponse.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .status(order.getStatus().name())
                .cancelType("FULL")
                .refundAmount(BigDecimal.ZERO)
                .cancelledAt(LocalDateTime.now())
                .cancelledItems(Collections.emptyList())
                .build();
    }

    public static OrderCancelResponse from(Order order,
                                           String cancelType,
                                           BigDecimal refundAmount,
                                           List<CancelledItem> cancelledItems,
                                           LocalDateTime cancelledAt) {
        return OrderCancelResponse.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .status(order.getStatus().name())
                .cancelType(cancelType)
                .refundAmount(refundAmount)
                .cancelledAt(cancelledAt)
                .cancelledItems(cancelledItems)
                .build();
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CancelledItem {
        private Long orderItemId;
        private Long productId;
        private int cancelledQuantity;
        private BigDecimal unitPrice;
        private BigDecimal subtotal;
    }
}
