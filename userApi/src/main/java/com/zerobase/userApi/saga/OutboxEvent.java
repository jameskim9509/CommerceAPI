package com.zerobase.userApi.saga;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
    @JdbcTypeCode(SqlTypes.VARCHAR)   // Flyway V4 가 VARCHAR(36) 으로 생성한 컬럼과 매핑 일치
    private UUID eventId;

    @Column(nullable = false, length = 100)
    private String topic;

    // Flyway V4 가 MEDIUMTEXT 로 생성한 컬럼과 정합. @Lob 기본 매핑(TINYTEXT) 과 충돌하지 않게 명시.
    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    public void markSent(Instant when) {
        this.sentAt = when;
    }
}
