package com.pms.order.domain.order.entity;

import com.pms.order.domain.product.entity.Product;
import com.pms.order.global.exception.BusinessException;
import com.pms.order.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "order_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "product_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal productPrice;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "cancelled_quantity", nullable = false)
    private int cancelledQuantity;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public OrderItem(Order order, Product product, String productName, BigDecimal productPrice, int quantity) {
        this.order = order;
        this.product = product;
        this.productName = productName;
        this.productPrice = productPrice;
        this.quantity = quantity;
        this.cancelledQuantity = 0;
    }

    public int getActiveQuantity() {
        return quantity - cancelledQuantity;
    }

    public boolean isFullyCancelled() {
        return cancelledQuantity >= quantity;
    }

    public void cancelQuantity(int qty) {
        if (qty <= 0) {
            throw new BusinessException(ErrorCode.INVALID_CANCEL_QUANTITY);
        }
        if (this.cancelledQuantity + qty > this.quantity) {
            throw new BusinessException(ErrorCode.CANCEL_QUANTITY_EXCEEDS_REMAINING);
        }
        this.cancelledQuantity += qty;
    }
}
