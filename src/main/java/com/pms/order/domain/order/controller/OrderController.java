package com.pms.order.domain.order.controller;

import com.pms.order.domain.order.dto.*;
import com.pms.order.domain.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createOrder(
            @RequestHeader("X-Member-Id") Long memberId,
            @Valid @RequestBody CreateOrderRequest request) {
        return orderService.createOrder(memberId, request);
    }

    @GetMapping("/{orderId}")
    public OrderResponse getOrder(
            @RequestHeader("X-Member-Id") Long memberId,
            @PathVariable Long orderId) {
        return orderService.getOrder(memberId, orderId);
    }

    @GetMapping
    public Page<OrderListResponse> getOrders(
            @RequestHeader("X-Member-Id") Long memberId,
            @PageableDefault(size = 20) Pageable pageable) {
        return orderService.getOrders(memberId, pageable);
    }

    @PostMapping("/{orderId}/cancel")
    public OrderCancelResponse cancelOrder(
            @RequestHeader("X-Member-Id") Long memberId,
            @PathVariable Long orderId,
            @Valid @RequestBody(required = false) CancelOrderRequest request) {
        return orderService.cancelOrder(memberId, orderId, request);
    }
}
