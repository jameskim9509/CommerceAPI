package com.zerobase.userApi.saga;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerobase.userApi.saga.event.SagaEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * ADR-004: Kafka 로 직접 보내는 대신 outbox_events 테이블에 INSERT 만 한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SagaEventPublisher {

    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(String topic, SagaEvent event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize event for topic " + topic, e);
        }
        outboxRepository.save(OutboxEvent.builder()
                .eventId(event.getEventId())
                .topic(topic)
                .payload(payload)
                .createdAt(Instant.now())
                .build());
        log.debug("Outbox enqueued: topic={} eventId={}", topic, event.getEventId());
    }
}
