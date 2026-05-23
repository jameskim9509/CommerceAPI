-- =============================================================================
-- orderApi V3: product_item 에 낙관적 락용 version 컬럼 추가
--  - ADR-002: 재고 차감 동시성(Lost Update) 방지를 위한 @Version 도입
--  - 기존 행은 0 으로 초기화. JPA 가 UPDATE 마다 자동 증가시킨다.
--  - Envers 의 product_item_aud 는 version 을 감사하지 않으므로 컬럼 추가 없음
--    (ProductItem.version 에 @NotAudited 명시).
-- =============================================================================

ALTER TABLE product_item ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
