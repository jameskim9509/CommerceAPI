-- =============================================================================
-- ADR-008 정합성 통합 시나리오 부하 시드 - userApi DB (user)
--
-- 작은 고객 풀(P ≪ 주문 수)로 "같은 고객 경합"을 강제해 ⑤ 잔액 Lost Update 를 발화시킨다.
--   - ctrich{1..300}  : 잔액 10,000,000 (충분) → 정상/hot 경합 주문의 주체
--   - ctbroke{1..60}  : 잔액 500 (부족)      → 결제 실패(④ NOT_ENOUGH_BALANCE) 발화
--
-- ★ 핵심(ADR-005 시드 버그 재발 방지):
--   결제 가능 잔액은 customer.balance 가 아니라 "customer_balance_history 최신 change_money" 로 판정된다
--   (CustomerBalanceHistoryService.changeBalance → findByCustomerIdRecent). 따라서 고객마다 기준 이력 행이
--   반드시 있어야 하며, 그 행의 change_money(=현재 running balance) 가 결제 가능액이다. 이력이 없으면
--   시작 잔액을 0 으로 보아 모든 결제가 NOT_ENOUGH_BALANCE 로 실패한다.
--
-- 자립 시드 규칙 (01-load-balancing 과 동일 원칙 — 필요한 모든 데이터를 스스로 만든다):
--   - seller(id=1) 도 이 시드가 직접 만든다 — order.sql 의 product.seller_id=1 및
--     k6(load-test-consistency.js) 카트 body(sellerId:1) 가 참조. 주입 순서 user.sql → order.sql.
--     이 스택은 전용 DB(name: consist)라 다른 시나리오/상시환경 seller 와 공존하지 않아 PK 충돌 없음.
--   - 이메일 접두사 ctrich / ctbroke 로 런 스코프를 잡는다 (verify-consistency.sh 와 동일 규칙).
--   - cleanup 은 자기 행(seller id=1, ^(ctrich|ctbroke)[0-9]+@qa\.test$)만 — 멱등 재주입 안전.
--   - password 평문 "password" (BCryptPasswordEncoder 호환), verify=TRUE(검증 우회), role=CUSTOMER.
-- =============================================================================

USE `user`;

-- ---------------- SELLER (id=1) — 이 시나리오가 직접 소유 ----------------
-- order.sql 의 product.seller_id=1 및 k6 카트 body(sellerId:1) 가 참조하는 판매자.
-- (자식 seller_roles → 부모 seller 순서로 정리)
DELETE FROM seller_roles WHERE seller_id = 1;
DELETE FROM seller       WHERE id = 1;

INSERT INTO seller
    (id, email, name, password, birth, phone_num, verify_expired_at, verification_code, verify, created_date, modified_date)
VALUES
    (1, 'ctseller1@qa.test', 'CT Seller 1',
     '$2b$10$8P14US/kur6TMqGsC90ro.r8nvQ8uOrD8zLut4XyA2Y9qvGQiYOzq',  -- bcrypt("password")
     '1990-01-01', '010-3000-0001', NOW() + INTERVAL 1 YEAR, NULL, b'1', NOW(), NOW());

INSERT INTO seller_roles (seller_id, roles) VALUES (1, 'ROLE_SELLER');

-- ---------------- cleanup (자기 행만, FK 순서) ----------------
DELETE FROM customer_roles           WHERE customer_id IN (SELECT id FROM (SELECT id FROM customer WHERE email REGEXP '^(ctrich|ctbroke)[0-9]+@qa\\.test$') AS t);
DELETE FROM customer_balance_history WHERE CUSTOMER_ID IN (SELECT id FROM (SELECT id FROM customer WHERE email REGEXP '^(ctrich|ctbroke)[0-9]+@qa\\.test$') AS t);
DELETE FROM customer                 WHERE email REGEXP '^(ctrich|ctbroke)[0-9]+@qa\\.test$';

-- ---------------- 숫자 테이블 (1..999) ----------------
-- a + b*10 + c*100 (+1) 로 1..1000 을 만들고 필요한 만큼만 필터.

-- rich 300명 (잔액 10,000,000)
INSERT INTO customer (email, name, password, birth, phone_num, verify_expired_at, verification_code, verify, balance, created_date, modified_date)
SELECT
    CONCAT('ctrich', n, '@qa.test'),
    CONCAT('CtRich', n),
    '$2b$10$8P14US/kur6TMqGsC90ro.r8nvQ8uOrD8zLut4XyA2Y9qvGQiYOzq',  -- bcrypt("password")
    DATE '1990-01-01',
    CONCAT('010-3001-', LPAD(n, 4, '0')),
    NOW(), 'ct-seed', 1, 10000000, NOW(6), NOW(6)
FROM (
    SELECT a.N + b.N * 10 + c.N * 100 + 1 AS n
    FROM (SELECT 0 N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) a
    CROSS JOIN (SELECT 0 N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) b
    CROSS JOIN (SELECT 0 N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) c
) numbers
WHERE n <= 300;

-- broke 60명 (잔액 500 → 1,000원 주문이 NOT_ENOUGH_BALANCE 로 실패)
INSERT INTO customer (email, name, password, birth, phone_num, verify_expired_at, verification_code, verify, balance, created_date, modified_date)
SELECT
    CONCAT('ctbroke', n, '@qa.test'),
    CONCAT('CtBroke', n),
    '$2b$10$8P14US/kur6TMqGsC90ro.r8nvQ8uOrD8zLut4XyA2Y9qvGQiYOzq',  -- bcrypt("password")
    DATE '1990-01-01',
    CONCAT('010-3060-', LPAD(n, 4, '0')),
    NOW(), 'ct-seed', 1, 500, NOW(6), NOW(6)
FROM (
    SELECT a.N + b.N * 10 + 1 AS n
    FROM (SELECT 0 N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) a
    CROSS JOIN (SELECT 0 N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) b
) numbers
WHERE n <= 60;

-- ROLE_CUSTOMER 부여
INSERT INTO customer_roles (customer_id, roles)
SELECT id, 'ROLE_CUSTOMER' FROM customer WHERE email REGEXP '^(ctrich|ctbroke)[0-9]+@qa\\.test$';

-- ★ 결제 가능액을 만드는 "기준 이력 행" (고객당 정확히 1행 → 최신행 change_money = running balance)
--   change_money = 현재 running balance, current_money = 직전 running balance(초기 0)
INSERT INTO customer_balance_history (change_money, current_money, from_message, description, CUSTOMER_ID, created_date, modified_date)
SELECT 10000000, 0, 'ct-seed', 'ct rich initial charge', id, NOW(6), NOW(6)
FROM customer WHERE email REGEXP '^ctrich[0-9]+@qa\\.test$';

INSERT INTO customer_balance_history (change_money, current_money, from_message, description, CUSTOMER_ID, created_date, modified_date)
SELECT 500, 0, 'ct-seed', 'ct broke initial charge', id, NOW(6), NOW(6)
FROM customer WHERE email REGEXP '^ctbroke[0-9]+@qa\\.test$';

-- ---------------- 검증 ----------------
SELECT 'seller'  AS pool, COUNT(*) AS customers FROM seller WHERE id = 1
UNION ALL
SELECT 'ctrich', COUNT(*) FROM customer WHERE email REGEXP '^ctrich[0-9]+@qa\\.test$'
UNION ALL
SELECT 'ctbroke', COUNT(*) FROM customer WHERE email REGEXP '^ctbroke[0-9]+@qa\\.test$'
UNION ALL
SELECT 'balance_history_rows', COUNT(*) FROM customer_balance_history h
    JOIN customer c ON h.CUSTOMER_ID = c.id WHERE c.email REGEXP '^(ctrich|ctbroke)[0-9]+@qa\\.test$';
