-- =============================================================================
-- orderApi V4: Choreography SAGA 도메인 테이블 (ADR-003)
--  - orders: 주문 상태 머신 (PENDING → PAID → CONFIRMED / FAILED)
--  - order_items: 주문 시점의 가격·수량 스냅샷
--  - processed_events: 컨슈머 멱등성 보장용 처리 이력 (event_id + consumer_name)
-- =============================================================================

CREATE TABLE orders (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    customer_id     BIGINT      NOT NULL,
    username        VARCHAR(255),
    status          VARCHAR(32) NOT NULL,
    total_price     INT         NOT NULL,
    failure_reason  VARCHAR(500),
    created_date    DATETIME(6),
    modified_date   DATETIME(6),
    PRIMARY KEY (id)
) ENGINE = InnoDB;

CREATE TABLE order_items (
    id              BIGINT NOT NULL AUTO_INCREMENT,
    order_id        BIGINT NOT NULL,
    product_item_id BIGINT NOT NULL,
    name            VARCHAR(255),
    count           INT    NOT NULL,
    price           INT    NOT NULL,
    created_date    DATETIME(6),
    modified_date   DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_order_items_orders
        FOREIGN KEY (order_id) REFERENCES orders (id)
) ENGINE = InnoDB;

CREATE TABLE processed_events (
    event_id        VARCHAR(36)  NOT NULL,
    consumer_name   VARCHAR(100) NOT NULL,
    processed_at    DATETIME(6),
    PRIMARY KEY (event_id, consumer_name)
) ENGINE = InnoDB;
