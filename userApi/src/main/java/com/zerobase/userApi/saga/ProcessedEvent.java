package com.zerobase.userApi.saga;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_events")
@IdClass(ProcessedEvent.Pk.class)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", length = 36)
    @JdbcTypeCode(SqlTypes.VARCHAR)   // Flyway V3 가 VARCHAR(36) 으로 생성한 컬럼과 매핑 일치
    private UUID eventId;

    @Id
    @Column(name = "consumer_name", length = 100)
    private String consumerName;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Pk implements Serializable {
        private UUID eventId;
        private String consumerName;
    }
}
