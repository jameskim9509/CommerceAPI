package com.zerobase.orderApi.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    PRODUCT_NOT_FOUND(HttpStatus.BAD_REQUEST, "해당하는 상품을 찾을 수 없습니다."),
    PRODUCT_ITEM_EXIST(HttpStatus.BAD_REQUEST, "같은 이름의 아이템이 이미 존재합니다."),
    PRODUCT_ITEM_NOT_FOUND(HttpStatus.BAD_REQUEST, "해당하는 아이템을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;
}