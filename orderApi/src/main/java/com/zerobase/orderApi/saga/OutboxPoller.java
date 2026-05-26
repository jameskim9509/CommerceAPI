package com.zerobase.orderApi.saga;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * ADR-004: outbox_events 의 미발행 row 를 주기적으로 Kafka 로 발행.
 *  - 한 row 의 send + sent_at 마킹은 같은 @Transactional 안.
 *  - 발행 실패 시 sent_at 미마킹 → 다음 주기 자동 재시도 (at-least-once).
 *  - 순서 보장을 위해 한 건 실패 시 이후 row 도 보류.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "outbox.poller.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class OutboxPoller {

    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(3);

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelayString = "${outbox.poller.delay-ms:1000}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> batch = outboxRepository.findTop100BySentAtIsNullOrderByCreatedAtAsc();
        for (OutboxEvent row : batch) {
            try {
                kafkaTemplate.send(row.getTopic(), row.getEventId().toString(), row.getPayload())
                        .get(SEND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                row.markSent(Instant.now());
            } catch (Exception e) {
                log.warn("Failed to publish outbox event {} on topic {}: {}",
                        row.getEventId(), row.getTopic(), e.getMessage());
                break;
            }
        }
    }
}
