package com.zerobase.userApi.saga;

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
