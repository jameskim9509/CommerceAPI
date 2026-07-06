-- =============================================================================
-- 정합성 통합 시나리오 seed - orderApi DB (orders)
--
-- 단일 "한정 재고" SKU 하나에 모든 주문을 몰아 재고 경합을 만든다.
--   - product/product_item id = 10001 (functional 9001+, load 1..500 과 겹치지 않음)
--   - count = 1200  → 누적 주문이 이를 초과하면 NOT_ENOUGH_ITEM_COUNT 로 FAILED+환불
--   - version = 0   → 병렬 재고 차감 시 낙관적 락 충돌(ObjectOptimisticLockingFailureException)
--   - price = 1000  → ctbroke(잔액 500) 는 결제 실패, ctrich(잔액 1000만) 는 결제 성공
--   - seller_id = 1 (orders DB 에 seller FK 없음 — 단순 값)
--
-- 이름은 k6(load-test-consistency.js) 의 cart/order 페이로드와 정확히 일치해야
-- refreshCart 가 메시지를 추가하지 않는다.
-- =============================================================================

USE orders;

-- 멱등성: 시나리오 자기 행만 정리
DELETE FROM order_items  WHERE order_id IN (SELECT id FROM (SELECT id FROM orders WHERE username LIKE 'ctrich%' OR username LIKE 'ctbroke%') AS t);
DELETE FROM orders       WHERE username LIKE 'ctrich%' OR username LIKE 'ctbroke%';
DELETE FROM product_item WHERE name LIKE 'CT-HOT%';
DELETE FROM product      WHERE name LIKE 'CT-HOT%';

-- 핫패스 문자열은 ASCII (refreshCart 가 cart↔DB 문자열을 비교하므로 인코딩 불일치 원천 차단)
INSERT INTO product (id, seller_id, name, description, created_date, modified_date) VALUES
    (10001, 1, 'CT-HOT-Limited', 'CT-HOT limited stock contention', NOW(6), NOW(6));

INSERT INTO product_item (id, seller_id, name, price, count, PRODUCT_ID, version, created_date, modified_date) VALUES
    (10001, 1, 'CT-HOT-Limited-Item', 1000, 1200, 10001, 0, NOW(6), NOW(6));

SELECT 'CT-HOT initial stock' AS msg, count FROM product_item WHERE id = 10001;
