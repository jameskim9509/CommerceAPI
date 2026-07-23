package com.zerobase.orderApi.idempotency;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

// =============================================================================
// [ADR-008 control 무방어 오버레이] 원하는 장애 ① 중복 주문/결제 방어(멱등 게이트) 제거.
//   execute() 가 Redis dedup 없이 매 요청을 그대로 실행 → 같은 Idempotency-Key 재전송이 새 주문 이중 생성.
//   빌드 중에만 원본 위에 덮어씀. 원본: orderApi/.../idempotency/IdempotencyService.java (execute() 만 무방어로 교체)
// =============================================================================
@Service
@Slf4j
public class IdempotencyService {

    public ResponseEntity<?> execute(String idempotencyKey, Supplier<ResponseEntity<?>> action) {
        // (control) 멱등 게이트 제거 — 게이트/캐시 없이 매 요청 실행.
        return action.get();
    }
}
