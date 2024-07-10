package com.zerobase.orderApi.exception;

import com.zerobase.orderApi.dto.ErrorResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class CustomExceptionHandler {

    @ExceptionHandler({CustomException.class})
    public ResponseEntity<ErrorResponseDto> customerExceptionHandler(
            CustomException e
    )
    {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponseDto.builder()
                                .errorCode(e.getErrorCode())
                                .message(e.getMessage())
                                .build());
    }
}
