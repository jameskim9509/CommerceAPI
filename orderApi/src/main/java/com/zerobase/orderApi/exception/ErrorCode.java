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
    NOT_ENOUGH_ITEM_COUNT(HttpStatus.BAD_REQUEST, "상품 아이템의 수량이 부족합니다.");

    private final HttpStatus status;
    private final String message;
}