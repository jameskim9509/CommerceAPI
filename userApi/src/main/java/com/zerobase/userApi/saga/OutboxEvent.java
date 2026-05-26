package com.zerobase.userApi.saga;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Transactional Outbox (ADR-004) 의 발행 대기 row.
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
