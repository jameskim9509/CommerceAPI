package com.zerobase.userApi.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    ALREADY_EXIST_USER(HttpStatus.BAD_REQUEST, "이미 존재하는 회원입니다."),
    SEND_EMAIL_ERROR(HttpStatus.BAD_REQUEST, "이메일 전송에 실패하였습니다"),
    VERIFICATION_FAILED(HttpStatus.BAD_REQUEST, "검증에 실패하였습니다.");

    private final HttpStatus status;
    private final String message;
}
