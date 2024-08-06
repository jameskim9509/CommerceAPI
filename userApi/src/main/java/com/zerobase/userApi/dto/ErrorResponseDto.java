package com.zerobase.userApi.dto;

import com.zerobase.userApi.exception.ErrorCode;
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
