-- =============================================================================
-- 기능/시나리오 QA seed (orderApi / orders DB) — 모든 환경에 자동 주입되는 "베이스 픽스처"
--   - 품절(재고 0)·다중 셀러 등 엣지케이스 포함 (수동/시나리오 테스트용)
--   - product_item.version 은 V3 마이그레이션의 DEFAULT 0 사용
--
-- load(k6) seed 와 한 DB 에서 공존하기 위한 규칙:
--   - seller_id 1·2 는 functional/user.sql 이 만든 seller PK 와 일치.
--   - product / product_item id 는 9001+ 를 "명시적으로" 점유한다.
--     load/order.sql 이 1..100 / 1..500 을 강제 INSERT 하므로 그 범위와 절대 겹치면 안 된다.
--   - cleanup 은 'QA-%' 자기 행만 (load 의 'QaProduct%' 는 건드리지 않는다).
--     order_items 는 product_item 에 FK 가 없으므로(스냅샷) 이름 기반 삭제로 안전.
-- =============================================================================

USE orders;

-- 멱등성: functional 자기 행만 정리 (자식 product_item → 부모 product 순서)
DELETE FROM product_item WHERE name LIKE 'QA-%';
DELETE FROM product      WHERE name LIKE 'QA-%';

-- ---------------- PRODUCTS (seller1=1) — 명시적 고 ID ----------------
INSERT INTO product (id, seller_id, name, description, created_date, modified_date) VALUES
    (9001, 1, 'QA-노트북 13인치', '시연용 가벼운 노트북',           NOW(), NOW()),
    (9002, 1, 'QA-무선마우스',    '광학 센서, 2.4GHz',             NOW(), NOW()),
    (9003, 1, 'QA-키보드 (품절)', '재고 0 상태로 시작되는 키보드',  NOW(), NOW());

-- ---------------- PRODUCTS (seller2=2) ----------------
INSERT INTO product (id, seller_id, name, description, created_date, modified_date) VALUES
    (9004, 2, 'QA-기술서적',  '클린 아키텍처',          NOW(), NOW()),
    (9005, 2, 'QA-모니터 27', '4K UHD 27인치 모니터',  NOW(), NOW());

-- ---------------- PRODUCT_ITEMS — 명시적 고 ID, PRODUCT_ID 직접 지정 ----------------
INSERT INTO product_item (id, seller_id, name, price, count, PRODUCT_ID, version, created_date, modified_date) VALUES
    (9001, 1, 'QA-노트북 13인치 / Silver',     1500000, 5,   9001, 0, NOW(), NOW()),
    (9002, 1, 'QA-노트북 13인치 / Space Gray', 1550000, 3,   9001, 0, NOW(), NOW()),
    (9003, 1, 'QA-무선마우스 / Black',           30000, 100, 9002, 0, NOW(), NOW()),
    (9004, 1, 'QA-무선마우스 / White',           30000, 50,  9002, 0, NOW(), NOW()),
    (9005, 1, 'QA-키보드 (품절) / Red',          80000, 0,   9003, 0, NOW(), NOW()),
    (9006, 2, 'QA-기술서적 / 1판',               28000, 200, 9004, 0, NOW(), NOW()),
    (9007, 2, 'QA-기술서적 / 개정판',            32000, 80,  9004, 0, NOW(), NOW()),
    (9008, 2, 'QA-모니터 27 / Matte',           400000, 3,   9005, 0, NOW(), NOW());

SELECT '--- FUNCTIONAL ORDER SEED DONE ---' AS msg, COUNT(*) AS products FROM product      WHERE name LIKE 'QA-%';
SELECT '---'                               AS msg, COUNT(*) AS items    FROM product_item WHERE name LIKE 'QA-%';
