package com.zerobase.orderApi.saga;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @Transactional
    public <E> void handle(UUID eventId, String consumerName, E event, Consumer<E> processor) {
        // [control/no-defense] ⑤ processed_events dedup 제거 — 중복/역순 재배달을 매번 재처리.
        processor.accept(event);
    }
}
