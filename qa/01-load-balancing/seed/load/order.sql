-- =============================================================================
-- ADR-005 시나리오 3 부하(k6) 시드 - orderApi DB (orders)
-- 100 개 product, 각각 ProductItem 5 개 (총 500 product_items)
-- 재고는 1_000_000 (재고 부족이 부하 시그널을 오염시키지 않도록)
--
-- 가격: 1000 ~ 5000 원 (5 단계)
-- seller_id: functional/user.sql 이 만든 seller1 의 PK(=1). functional 이 먼저 주입된다.
-- product / product_item id = 1..100 / 1..500 강제 INSERT (load-test.js 의 산술 매핑과 일치).
--   functional/order.sql 은 9001+ 를 쓰므로 이 범위와 겹치지 않는다.
-- cleanup 은 'QaProduct%' (자기 행)만 — functional 의 'QA-%' 는 건드리지 않는다.
-- =============================================================================

USE orders;

-- 기존 부하 시드 정리 (customer<digits> 주문 + QaProduct 상품만)
DELETE FROM order_items WHERE order_id IN (SELECT id FROM (SELECT id FROM orders WHERE username REGEXP '^customer[0-9]+@qa\\.test$') AS t);
DELETE FROM orders WHERE username REGEXP '^customer[0-9]+@qa\\.test$';
DELETE FROM product_item WHERE name LIKE 'QaProduct%';
DELETE FROM product WHERE name LIKE 'QaProduct%';
DELETE FROM outbox_events WHERE topic LIKE 'qa-%' OR created_at < NOW() - INTERVAL 30 DAY;
DELETE FROM processed_events WHERE consumer_name LIKE 'qa-%';

-- seller_id 는 functional 이 만든 seller1 (id=1) 을 재사용.
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
