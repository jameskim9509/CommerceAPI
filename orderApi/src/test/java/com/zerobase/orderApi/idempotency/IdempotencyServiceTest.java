package com.zerobase.orderApi.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerobase.orderApi.exception.CustomException;
import com.zerobase.orderApi.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOps;

    private IdempotencyService idempotencyService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        idempotencyService = new IdempotencyService(redisTemplate, objectMapper);
    }

    @Test
    @DisplayName("Idempotency-Key 가 null 이면 IDEMPOTENCY_KEY_REQUIRED 예외")
    void nullKey_throws() {
        assertThatThrownBy(() -> idempotencyService.execute(null,
                () -> ResponseEntity.ok("x")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
    }

    @Test
    @DisplayName("Idempotency-Key 가 빈 문자열이면 IDEMPOTENCY_KEY_REQUIRED 예외")
    void blankKey_throws() {
        assertThatThrownBy(() -> idempotencyService.execute("  ",
                () -> ResponseEntity.ok("x")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
    }

    @Test
    @DisplayName("새 키 — SETNX 선점 후 action 실행, 결과를 COMPLETED 로 캐싱")
    void newKey_executesAndCaches() {
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .willReturn(true);

        ResponseEntity<?> result = idempotencyService.execute("K-new",
                () -> ResponseEntity.ok(Map.of("orderId", 1)));

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        verify(valueOps).set(anyString(),
                argThat(s -> s.toString().contains("COMPLETED") && s.toString().contains("orderId")),
                any(Duration.class));
    }

    @Test
    @DisplayName("같은 키가 IN_PROGRESS — 409 DUPLICATE_REQUEST_IN_PROGRESS, action 미실행")
    void inProgress_throwsConflict() throws Exception {
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .willReturn(false);
        given(valueOps.get(anyString()))
                .willReturn(objectMapper.writeValueAsString(IdempotencyEntry.inProgress()));

        @SuppressWarnings("unchecked")
        Supplier<ResponseEntity<?>> action = mock(Supplier.class);

        assertThatThrownBy(() -> idempotencyService.execute("K-busy", action))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_REQUEST_IN_PROGRESS);

        verifyNoInteractions(action);
    }

    @Test
    @DisplayName("같은 키가 COMPLETED — 캐시된 응답 반환, action 미실행")
    void completed_returnsCachedResponse() throws Exception {
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .willReturn(false);
        given(valueOps.get(anyString()))
                .willReturn(objectMapper.writeValueAsString(
                        IdempotencyEntry.completed(200, Map.of("orderId", 42))));

        @SuppressWarnings("unchecked")
        Supplier<ResponseEntity<?>> action = mock(Supplier.class);
        ResponseEntity<?> result = idempotencyService.execute("K-done", action);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) result.getBody();
        assertThat(body).containsEntry("orderId", 42);
        verifyNoInteractions(action);
    }

    @Test
    @DisplayName("같은 키가 COMPLETED(실패 응답) — 실패 상태와 ErrorResponseDto 그대로 반환")
    void completed_failureResponse_returned() throws Exception {
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .willReturn(false);
        given(valueOps.get(anyString()))
                .willReturn(objectMapper.writeValueAsString(
                        IdempotencyEntry.completed(400, Map.of(
                                "errorCode", "PAYMENT_ERROR",
                                "message", "결제에 실패하였습니다. 잔액을 확인해 주세요."))));

        ResponseEntity<?> result = idempotencyService.execute("K-failed", () -> {
            throw new IllegalStateException("이 람다는 호출되면 안 됨");
        });

        assertThat(result.getStatusCode().value()).isEqualTo(400);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) result.getBody();
        assertThat(body).containsEntry("errorCode", "PAYMENT_ERROR");
    }

    @Test
    @DisplayName("action 에서 CustomException 발생 시 실패 응답을 COMPLETED 로 캐싱")
    void exception_cachesFailureResponse() {
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .willReturn(true);

        ResponseEntity<?> result = idempotencyService.execute("K-fail", () -> {
            throw new CustomException(ErrorCode.NOT_ENOUGH_ITEM_COUNT);
        });

        assertThat(result.getStatusCode().value()).isEqualTo(400);
        verify(valueOps).set(anyString(),
                argThat(s -> s.toString().contains("COMPLETED")
                        && s.toString().contains("NOT_ENOUGH_ITEM_COUNT")),
                any(Duration.class));
    }
}
