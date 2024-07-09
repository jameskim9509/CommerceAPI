package com.zerobase.orderApi.dto;

import com.zerobase.orderApi.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ErrorResponseDto {
    private ErrorCode errorCode;
    private String message;
}
