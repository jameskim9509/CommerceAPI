package com.zerobase.orderApi.exception;

import com.zerobase.orderApi.dto.ErrorResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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
                .status(e.getErrorCode().getStatus())
                .body(ErrorResponseDto.builder()
                                .errorCode(e.getErrorCode())
                                .message(e.getMessage())
                                .build());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponseDto> optimisticLockHandler(
            ObjectOptimisticLockingFailureException e
    )
    {
        log.warn("Optimistic lock conflict: {}", e.getMessage());
        ErrorCode code = ErrorCode.STOCK_CONFLICT;
        return ResponseEntity
                .status(code.getStatus())
                .body(ErrorResponseDto.builder()
                        .errorCode(code)
                        .message(code.getMessage())
                        .build());
    }
}
