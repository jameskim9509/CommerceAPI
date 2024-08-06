package com.zerobase.userApi.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    ALREADY_EXIST_USER(HttpStatus.BAD_REQUEST, "이미 존재하는 회원입니다."),
    SEND_EMAIL_ERROR(HttpStatus.BAD_REQUEST, "이메일 전송에 실패하였습니다"),
    VERIFICATION_FAILED(HttpStatus.BAD_REQUEST, "검증에 실패하였습니다."),
    VERIFICATION_REQUIRED(HttpStatus.BAD_REQUEST, "검증되지 않은 유저입니다."),
    USER_NOT_FOUND(HttpStatus.BAD_REQUEST, "일치하는 유저가 없습니다."),
    NOT_ENOUGH_BALANCE(HttpStatus.BAD_REQUEST, "잔액이 부족합니다.");

    private final HttpStatus status;
    private final String message;
}
