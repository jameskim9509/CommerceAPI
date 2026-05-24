-- =============================================================================
-- userApi V3: Choreography SAGA 컨슈머 멱등성 보장 테이블 (ADR-003)
--  - userApi 의 PaymentConsumer / RefundConsumer 가 같은 이벤트의 중복 배달을
--    감지하기 위해 (event_id, consumer_name) 쌍으로 처리 이력을 기록.
-- =============================================================================

CREATE TABLE processed_events (
    event_id        VARCHAR(36)  NOT NULL,
    consumer_name   VARCHAR(100) NOT NULL,
    processed_at    DATETIME(6),
    PRIMARY KEY (event_id, consumer_name)
) ENGINE = InnoDB;
