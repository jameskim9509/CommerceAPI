package com.zerobase.orderApi.saga;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotentEventHandler {

    private final ProcessedEventRepository processedEventRepository;

    // [ADR-008 통합 시나리오] 원하는 장애 ⑥ 이벤트 중복/역순 배달에 대한 방어(processed_events dedup) on/off.
    // 기본 true → treatment. control(무방어) 빌드만 false 로 내려 중복/역순 재배달을 그대로 재처리한다.
    @Value("${consistency.defense.dedup:true}")
    private boolean dedupDefenseEnabled;

    @Transactional
    public <E> void handle(UUID eventId, String consumerName, E event, Consumer<E> processor) {
        // control(무방어): dedup 없이 매 배달을 재처리 → 재고 이중 차감·상태 재전이 등 중복 부작용 노출.
        if (!dedupDefenseEnabled) {
            processor.accept(event);
            return;
        }
        if (eventId == null) {
            log.warn("Event without eventId received for consumer={}, processing without idempotency", consumerName);
            processor.accept(event);
            return;
        }
        if (processedEventRepository.existsByEventIdAndConsumerName(eventId, consumerName)) {
            log.info("Event {} already processed by {}, skipping", eventId, consumerName);
            return;
        }
        processor.accept(event);
        processedEventRepository.save(ProcessedEvent.builder()
                .eventId(eventId)
                .consumerName(consumerName)
                .processedAt(Instant.now())
                .build());
    }
}
