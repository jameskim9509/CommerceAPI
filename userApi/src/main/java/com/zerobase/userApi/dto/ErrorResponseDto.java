package com.zerobase.userApi.dto;

import com.zerobase.userApi.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Builder;

@AllArgsConstructor
@Builder
public class ErrorResponseDto {
    private ErrorCode errorCode;
    private String message;
}
