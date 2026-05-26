package com.zerobase.orderApi.saga;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Transactional Outbox (ADR-004) 의 발행 대기 row.
 *  - 비즈니스 트랜잭션에서 INSERT 되어 같은 commit 으로 영속화된다.
 *  - OutboxPoller 가 sent_at IS NULL 인 row 를 골라 Kafka 로 발행하고 sent_at 을 채운다.
 */
@Entity
@Table(name = "outbox_events", indexes = {
        @Index(name = "idx_unsent", columnList = "sent_at, created_at")
})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent {

    @Id
    @Column(name = "event_id", length = 36)
    private UUID eventId;

    @Column(nullable = false, length = 100)
    private String topic;

    @Lob
    @Column(nullable = false)
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    public void markSent(Instant when) {
        this.sentAt = when;
    }
}
