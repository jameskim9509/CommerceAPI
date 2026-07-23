package com.zerobase.userApi.saga;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.function.Consumer;

// =============================================================================
// [ADR-008 control 무방어 오버레이] ⑥ processed_events dedup 제거 + ⑤ 낙관적 락 재시도 제거
//   (⑤ @Version 자체는 Customer 오버레이가 제거). dedup·재시도 없이 한 트랜잭션으로 처리만.
//   → 중복/역순 재배달 재처리(중복 결제·환불) + 동시 결제/환불 Lost Update.
//   빌드 중에만 원본 위에 덮어씀. 원본: userApi/.../saga/IdempotentEventHandler.java
// =============================================================================
@Component
public class IdempotentEventHandler {

    private final TransactionTemplate transactionTemplate;

    public IdempotentEventHandler(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public <E> void handle(UUID eventId, String consumerName, E event, Consumer<E> processor) {
        // (control) dedup·재시도 없음 — 처리만.
        transactionTemplate.executeWithoutResult(status -> processor.accept(event));
    }
}
