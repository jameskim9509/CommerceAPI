package com.zerobase.orderApi.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    PRODUCT_NOT_FOUND(HttpStatus.BAD_REQUEST, "해당하는 상품을 찾을 수 없습니다."),
    PRODUCT_ITEM_EXIST(HttpStatus.BAD_REQUEST, "같은 이름의 아이템이 이미 존재합니다."),
    PRODUCT_ITEM_NOT_FOUND(HttpStatus.BAD_REQUEST, "해당하는 아이템을 찾을 수 없습니다."),

    CART_CHANGE_FAILED(HttpStatus.BAD_REQUEST, "장바구니를 수정하는데 실패하였습니다."),
    NOT_ENOUGH_ITEM_COUNT(HttpStatus.BAD_REQUEST, "상품 아이템의 수량이 부족합니다."),

    PAYMENT_ERROR(HttpStatus.BAD_REQUEST, "결제에 실패하였습니다. 잔액을 확인해 주세요."),
    CART_CHECK_REQUIRED(HttpStatus.BAD_REQUEST, "장바구니 확인이 필요합니다. 조회를 수행해주세요"),
    NOT_VALID_ORDER(HttpStatus.BAD_REQUEST, "주문 정보가 잘못되었습니다. 확인해주세요."),
    CART_NOT_EXIST(HttpStatus.BAD_REQUEST, "장바구니가 존재하지 않습니다.");

    private final HttpStatus status;
    private final String message;
}