-- =============================================================================
-- orderApi V5: Transactional Outbox (ADR-004) 발행 대기 테이블
--  - 비즈니스 트랜잭션에서 INSERT 되어 같은 commit 단위로 영속화
--  - OutboxPoller 가 sent_at IS NULL 인 row 를 골라 Kafka 로 발행하고
--    성공 시 sent_at 을 채운다.
-- =============================================================================

CREATE TABLE outbox_events (
    event_id      VARCHAR(36)  NOT NULL,
    topic         VARCHAR(100) NOT NULL,
    payload       MEDIUMTEXT   NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    sent_at       DATETIME(6),
    PRIMARY KEY (event_id),
    INDEX idx_unsent (sent_at, created_at)
) ENGINE = InnoDB;
