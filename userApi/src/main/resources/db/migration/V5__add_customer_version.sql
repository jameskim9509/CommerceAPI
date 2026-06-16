-- =============================================================================
-- userApi V5: customer 에 낙관적 락용 version 컬럼 추가
--  - ADR-002 의 낙관적 락을 재고(orderApi product_item)에 이어 잔액에도 적용.
--    동시 결제·환불(PaymentConsumer ↔ RefundConsumer)의 잔액 Lost Update 방지.
--  - 기존 행은 0 으로 초기화. JPA 가 UPDATE 마다 자동 증가시킨다.
--  - Envers 의 customer_aud 는 version 을 감사하지 않으므로 컬럼 추가 없음
--    (Customer.version 에 @NotAudited 명시).
-- =============================================================================

ALTER TABLE customer ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
