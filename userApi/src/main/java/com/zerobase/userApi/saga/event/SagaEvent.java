package com.zerobase.userApi.saga.event;

import java.util.UUID;

/**
 * SAGA 이벤트의 공통 마커 인터페이스.
 * 모든 이벤트는 발행자가 생성한 고유 UUID 를 노출해야 한다.
 *
 * - 발행 측(outbox row PK) 과 컨슈머 측(ProcessedEvent 멱등성 키) 에서 동일 UUID 를 공유한다.
 */
public interface SagaEvent {
    UUID getEventId();
}
