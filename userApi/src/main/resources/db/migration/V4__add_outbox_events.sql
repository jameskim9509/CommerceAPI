-- =============================================================================
-- userApi V4: Transactional Outbox (ADR-004) 발행 대기 테이블
--  - PaymentConsumer / RefundConsumer 의 SAGA 이벤트 발행 (PaymentDeducted /
--    PaymentFailed / PaymentReverted) 을 컨슈머 트랜잭션과 같은 commit 단위로
--    영속화하기 위해.
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
