-- =============================================================================
-- ADR-005 시나리오 3 QA 시드 - orderApi DB (orders)
-- 100 개 product, 각각 ProductItem 5 개 (총 500 product_items)
-- 재고는 1_000_000 (재고 부족이 부하 시그널을 오염시키지 않도록)
--
-- 가격: 1000 ~ 5000 원 (5 단계)
-- seller_id: userApi 의 seller@qa.test 의 id (외부 키 — 모듈 간 약결합)
-- =============================================================================

-- 기존 시드 정리
DELETE FROM order_items WHERE order_id IN (SELECT id FROM (SELECT id FROM orders WHERE username LIKE 'customer%@qa.test') AS t);
DELETE FROM orders WHERE username LIKE 'customer%@qa.test';
DELETE FROM product_item WHERE seller_id IN (SELECT id FROM (SELECT id FROM product WHERE name LIKE 'QaProduct%') AS t);
DELETE FROM product WHERE name LIKE 'QaProduct%';
DELETE FROM outbox_events WHERE topic LIKE 'qa-%' OR created_at < NOW() - INTERVAL 30 DAY;
DELETE FROM processed_events WHERE consumer_name LIKE 'qa-%';

-- AUTO_INCREMENT 리셋 (반복 시드 시 ID 가 1..100/500 으로 안정되게)
ALTER TABLE product AUTO_INCREMENT = 1;
ALTER TABLE product_item AUTO_INCREMENT = 1;

-- seller_id 는 userApi.seller 의 id 를 가정.
-- docker-compose 환경에서는 시드 순서를 userApi 시드 -> orderApi 시드 로 보장.
-- 본 스크립트는 단순화를 위해 seller_id=1 로 가정 (userApi 시드의 첫 seller).
SET @seller_id = 1;

-- 100 products. 명시적 ID 사용으로 product.id = n 보장.
INSERT INTO product (id, seller_id, name, description, created_date, modified_date)
SELECT
    n AS id,
    @seller_id,
    CONCAT('QaProduct', LPAD(n, 3, '0')),
    CONCAT('Product description ', n),
    NOW(6),
    NOW(6)
FROM (
    SELECT a.N + b.N * 10 + 1 AS n
    FROM (SELECT 0 N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) a
    CROSS JOIN (SELECT 0 N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) b
) numbers
WHERE n <= 100
ORDER BY n;

-- 각 product 당 5 개 ProductItem.
-- 명시적 ID 로 INSERT 해서 product_item.id 와 product.id 매핑을 결정적으로 만든다:
--   product_item.id = (product.id - 1) * 5 + item_n   (item_n = 1..5)
--   → product 1 items: 1..5, product 2 items: 6..10, ..., product 100 items: 496..500
INSERT INTO product_item (id, seller_id, name, price, count, PRODUCT_ID, version, created_date, modified_date)
SELECT
    (p.id - 1) * 5 + items.item_n AS id,
    @seller_id,
    CONCAT(p.name, '-Item', items.item_n),
    1000 * items.item_n,           -- 1000, 2000, 3000, 4000, 5000 원
    1000000,                        -- 재고 충분
    p.id,
    0,                              -- @Version 초기값
    NOW(6),
    NOW(6)
FROM product p
CROSS JOIN (SELECT 1 AS item_n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5) items
WHERE p.name LIKE 'QaProduct%'
ORDER BY p.id, items.item_n;

-- 검증
SELECT 'products' AS table_name, COUNT(*) AS cnt FROM product WHERE name LIKE 'QaProduct%'
UNION ALL
SELECT 'product_items', COUNT(*) FROM product_item pi JOIN product p ON pi.PRODUCT_ID = p.id WHERE p.name LIKE 'QaProduct%';
