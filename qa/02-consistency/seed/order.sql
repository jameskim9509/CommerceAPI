-- =============================================================================
-- ADR-008 정합성 통합 시나리오 부하 시드 - orderApi DB (orders)
--
-- 두 종류의 SKU:
--   1) hot SKU (한정 재고) — product/product_item id 10001, count=1000 ≪ 경합 수요(8% ≈ 8,000@10만)
--        → ② 재고 낙관적 락 경합 / 초과판매 방어 + ③ 재고 소진→환불 을 발화.
--   2) 정상 SKU 50종 × 5아이템 (id 20001.., 재고 10,000,000 충분) — 90% 정상 + 2% 멱등 재전송용.
--
-- ★ 이름/설명/가격/셀러는 k6(load-test-consistency.js)가 보내는 payload 와 "정확히" 일치해야 한다.
--   CartService.refreshCart 가 product.name/description, item.name/price 를 DB 와 비교해 다르면 메시지를 달고
--   OrderService.order 가 CART_CHECK_REQUIRED 로 거절한다 → SAGA 진입 자체가 막혀 측정이 오염된다.
--   메모리 규칙대로 hot-path 문자열은 전부 ASCII.
--
-- 자립 시드 규칙 (01-load-balancing 과 동일 원칙):
--   - seller_id 1 = 이 시나리오 user.sql 의 seller (자립 — user.sql 을 먼저 주입).
--   - 이 스택은 전용 DB(name: consist)라 id 범위 10001 / 20001.. 을 이 시드만 사용한다.
--   - cleanup 은 자기 행(CT-HOT% / CtNormal% / username ctrich)만 — 멱등 재주입 안전.
--   - product_item.version = 0 (V3 DEFAULT) — @Version 낙관적 락 초기값.
-- =============================================================================

USE orders;

-- ---------------- cleanup (자기 행만) ----------------
DELETE FROM order_items WHERE order_id IN (SELECT id FROM (SELECT id FROM orders WHERE username REGEXP '^ctrich[0-9]+@qa\\.test$') AS t);
DELETE FROM orders            WHERE username REGEXP '^ctrich[0-9]+@qa\\.test$';
DELETE FROM product_item      WHERE name LIKE 'CT-HOT%' OR name LIKE 'CtNormal%';
DELETE FROM product           WHERE name LIKE 'CT-HOT%' OR name LIKE 'CtNormal%';

SET @seller_id = 1;

-- ---------------- hot SKU (한정 재고 count=1000) ----------------
INSERT INTO product (id, seller_id, name, description, created_date, modified_date) VALUES
    (10001, @seller_id, 'CT-HOT-Limited', 'CT hot limited SKU', NOW(6), NOW(6));

INSERT INTO product_item (id, seller_id, name, price, count, PRODUCT_ID, version, created_date, modified_date) VALUES
    (10001, @seller_id, 'CT-HOT-Limited-Item', 1000, 1000, 10001, 0, NOW(6), NOW(6));

-- ---------------- 정상 SKU 50 products (id 20001..20050) ----------------
INSERT INTO product (id, seller_id, name, description, created_date, modified_date)
SELECT
    20000 + n AS id,
    @seller_id,
    CONCAT('CtNormal', LPAD(n, 3, '0')),
    CONCAT('CT normal product ', n),
    NOW(6), NOW(6)
FROM (
    SELECT a.N + b.N * 10 + 1 AS n
    FROM (SELECT 0 N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) a
    CROSS JOIN (SELECT 0 N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) b
) numbers
WHERE n <= 50
ORDER BY n;

-- 각 정상 product 당 5개 아이템 (id 20000 + (p-1)*5 + k, k=1..5), 재고 10,000,000, price 1000*k
--   → product 20001 items 20001..20005, product 20002 items 20006..20010, ...
INSERT INTO product_item (id, seller_id, name, price, count, PRODUCT_ID, version, created_date, modified_date)
SELECT
    20000 + (p.n - 1) * 5 + items.k AS id,
    @seller_id,
    CONCAT('CtNormal', LPAD(p.n, 3, '0'), '-Item', items.k),
    1000 * items.k,
    10000000,
    20000 + p.n,
    0,
    NOW(6), NOW(6)
FROM (
    SELECT a.N + b.N * 10 + 1 AS n
    FROM (SELECT 0 N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) a
    CROSS JOIN (SELECT 0 N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) b
    WHERE (a.N + b.N * 10 + 1) <= 50
) p
CROSS JOIN (SELECT 1 AS k UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5) items
ORDER BY id;

-- ---------------- 검증 ----------------
SELECT 'hot_stock'      AS metric, count AS value FROM product_item WHERE id = 10001
UNION ALL
SELECT 'normal_products', COUNT(*) FROM product      WHERE name LIKE 'CtNormal%'
UNION ALL
SELECT 'normal_items',    COUNT(*) FROM product_item WHERE name LIKE 'CtNormal%';
