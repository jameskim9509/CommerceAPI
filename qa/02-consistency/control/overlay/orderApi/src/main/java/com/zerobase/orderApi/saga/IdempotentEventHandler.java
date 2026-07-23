package com.zerobase.orderApi.saga;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.function.Consumer;

// =============================================================================
// [ADR-008 control 무방어 오버레이] 원하는 장애 ⑥ 이벤트 중복/역순 배달 방어(processed_events dedup) 제거.
//   dedup·마커 기록 없이 매 배달을 재처리 → 재고 이중 차감·상태 재전이 등 중복 부작용 노출.
//   빌드 중에만 원본 위에 덮어씀. 원본: orderApi/.../saga/IdempotentEventHandler.java
// =============================================================================
@Component
public class IdempotentEventHandler {

    @Transactional
    public <E> void handle(UUID eventId, String consumerName, E event, Consumer<E> processor) {
        // (control) dedup 없이 처리만 (한 트랜잭션으로).
        processor.accept(event);
    }
}
