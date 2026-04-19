package com.pms.order.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "잘못된 요청 파라미터입니다."),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND", "회원을 찾을 수 없습니다."),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "주문을 찾을 수 없습니다."),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "상품을 찾을 수 없습니다."),
    CART_NOT_FOUND(HttpStatus.NOT_FOUND, "CART_NOT_FOUND", "장바구니를 찾을 수 없습니다."),
    CART_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "CART_ITEM_NOT_FOUND", "장바구니 상품을 찾을 수 없습니다."),
    CART_EMPTY(HttpStatus.BAD_REQUEST, "CART_EMPTY", "장바구니가 비어있습니다."),
    PRODUCT_NOT_AVAILABLE(HttpStatus.CONFLICT, "PRODUCT_NOT_AVAILABLE", "상품이 판매 불가 상태입니다."),
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", "재고가 부족합니다."),
    INVALID_ORDER_STATUS(HttpStatus.CONFLICT, "INVALID_ORDER_STATUS", "주문 상태 전이가 불가합니다."),
    PAYMENT_FAILED(HttpStatus.BAD_GATEWAY, "PAYMENT_FAILED", "외부 결제 시스템 실패입니다."),
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "결제 정보를 찾을 수 없습니다."),
    DUPLICATE_REQUEST(HttpStatus.CONFLICT, "DUPLICATE_REQUEST", "중복 요청입니다."),
    CANCEL_WINDOW_EXPIRED(HttpStatus.CONFLICT, "CANCEL_WINDOW_EXPIRED", "취소 가능 시간이 지났습니다."),
    INVALID_CANCEL_QUANTITY(HttpStatus.BAD_REQUEST, "INVALID_CANCEL_QUANTITY", "취소 수량이 유효하지 않습니다."),
    CANCEL_QUANTITY_EXCEEDS_REMAINING(HttpStatus.CONFLICT, "CANCEL_QUANTITY_EXCEEDS_REMAINING", "취소 가능 수량을 초과했습니다."),
    INVALID_CANCEL_REQUEST(HttpStatus.BAD_REQUEST, "INVALID_CANCEL_REQUEST", "취소 요청이 유효하지 않습니다."),
    ORDER_ITEM_NOT_IN_ORDER(HttpStatus.FORBIDDEN, "ORDER_ITEM_NOT_IN_ORDER", "해당 주문의 상품이 아닙니다."),
    PARTIAL_CANCEL_DISABLED(HttpStatus.CONFLICT, "PARTIAL_CANCEL_DISABLED", "부분 취소가 비활성화되어 있습니다."),
    REFUND_EXCEEDS_PAYMENT(HttpStatus.CONFLICT, "REFUND_EXCEEDS_PAYMENT", "환불 요청액이 결제 금액을 초과합니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "서버 내부 오류입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
