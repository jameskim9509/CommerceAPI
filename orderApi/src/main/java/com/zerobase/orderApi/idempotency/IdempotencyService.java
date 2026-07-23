package com.zerobase.orderApi.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerobase.orderApi.dto.ErrorResponseDto;
import com.zerobase.orderApi.exception.CustomException;
import com.zerobase.orderApi.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * ADR-001 의 진입점 멱등성 상태머신을 구현한다.
 *  - 키 없음:     SETNX IN_PROGRESS 로 선점 → action 실행 후 COMPLETED 로 캐싱
 *  - IN_PROGRESS: 409 Conflict 반환 (재처리 없음)
 *  - COMPLETED:   캐시된 응답을 그대로 반환 (성공/실패 무관)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private static final String KEY_PREFIX = "idem:order:";
    private static final Duration COMPLETED_TTL = Duration.ofHours(24);
    private static final Duration IN_PROGRESS_TTL = Duration.ofMinutes(2);

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public ResponseEntity<?> execute(String idempotencyKey, Supplier<ResponseEntity<?>> action) {
        // [control/no-defense] ① 멱등 게이트 제거 — 게이트/캐시 없이 매 요청 실행 (같은 키 재전송 → 새 주문 이중 생성).
        return action.get();
    }

    private boolean tryAcquire(String redisKey) {
        return Boolean.TRUE.equals(
                redisTemplate.opsForValue()
                        .setIfAbsent(redisKey, serialize(IdempotencyEntry.inProgress()), IN_PROGRESS_TTL));
    }

    private ResponseEntity<?> resolveExisting(String redisKey) {
        IdempotencyEntry existing = read(redisKey);
        if (existing == null) {
            // 매우 희박 (TTL 만료 등) — 다음 호출에서 자연 회복
            throw new CustomException(ErrorCode.DUPLICATE_REQUEST_IN_PROGRESS);
        }
        if (existing.getState() == IdempotencyEntry.State.COMPLETED) {
            return ResponseEntity.status(existing.getHttpStatus()).body(existing.getBody());
        }
        throw new CustomException(ErrorCode.DUPLICATE_REQUEST_IN_PROGRESS);
    }

    private void cache(String redisKey, int status, Object body) {
        IdempotencyEntry entry = IdempotencyEntry.completed(status, body);
        redisTemplate.opsForValue().set(redisKey, serialize(entry), COMPLETED_TTL);
    }

    private IdempotencyEntry read(String redisKey) {
        Object value = redisTemplate.opsForValue().get(redisKey);
        if (value == null) return null;
        try {
            return objectMapper.readValue((String) value, IdempotencyEntry.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse idempotency entry for key {}", redisKey, e);
            return null;
        }
    }

    private String serialize(IdempotencyEntry entry) {
        try {
            return objectMapper.writeValueAsString(entry);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize idempotency entry", e);
        }
    }
}
