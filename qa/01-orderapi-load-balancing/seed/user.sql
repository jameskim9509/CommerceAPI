-- =============================================================================
-- ADR-005 시나리오 3 부하(k6) 시드 - userApi DB (user)
-- 이 시나리오의 "자립 시드": 필요한 모든 데이터(seller + 대량 customer)를 스스로 만든다.
-- 이메일 검증 (mailgun) 단계를 우회하기 위해 verify=true 로 직접 INSERT.
--
-- seller (id=1):
--   order.sql 의 product.seller_id=1 및 load-test.js 카트 body(sellerId:1) 가 참조.
--   qa 스택은 별도 DB(name: qa)라 상시환경 seller 와 공존하지 않아 PK 충돌 없음.
--   주입 순서 user.sql → order.sql (product 가 이 seller 를 참조).
--
-- 1000 명의 customer:
--   email: customer{i}@qa.test (i=1..1000)  ← load-test.js 가 이 규칙으로 로그인
--   password: "password" (BCrypt — Spring BCryptPasswordEncoder 호환)
--   verify: TRUE (검증 우회) / balance: 10_000_000 / role: CUSTOMER
--
-- customer_balance_history 초기 행 (필수):
--   결제 검증은 customer.balance 컬럼이 아니라 이 테이블의 최신 행을 본다.
--   행이 없으면 잔액 0 으로 간주돼 SAGA 결제 단계가 전건 실패한다 (아래 주석 참조).
--
-- cleanup 은 자기 행만 (seller id=1, customer<digits>@qa.test) — 멱등 재주입 안전.
-- =============================================================================

USE `user`;

-- ---------------- SELLER (id=1) — 이 시나리오가 직접 소유 ----------------
-- product.seller_id=1 및 load-test.js 카트 body(sellerId:1) 가 참조하는 판매자.
-- (자식 seller_roles → 부모 seller 순서로 정리)
DELETE FROM seller_roles WHERE seller_id = 1;
DELETE FROM seller       WHERE id = 1;

INSERT INTO seller
    (id, email, name, password, birth, phone_num, verify_expired_at, verification_code, verify, created_date, modified_date)
VALUES
    (1, 'seller1@qa.test', 'QA Seller 1',
     '$2b$10$IR3iJ.0INCxQFnnjyQcKfOIiWtnArIsx2NN1J7VyV0is2BoTUly2G',
     '1990-01-01', '010-0000-0001', NOW() + INTERVAL 1 YEAR, NULL, b'1', NOW(), NOW());

INSERT INTO seller_roles (seller_id, roles) VALUES (1, 'ROLE_SELLER');

-- 기존 부하 시드 정리 (FK 순서 주의, REGEXP 로 customer1..1000 만)
DELETE FROM customer_roles           WHERE customer_id IN (SELECT id FROM (SELECT id FROM customer WHERE email REGEXP '^customer[0-9]+@qa\\.test$') AS t);
DELETE FROM customer_balance_history WHERE CUSTOMER_ID IN (SELECT id FROM (SELECT id FROM customer WHERE email REGEXP '^customer[0-9]+@qa\\.test$') AS t);
DELETE FROM customer                 WHERE email REGEXP '^customer[0-9]+@qa\\.test$';

-- 1000 customers (numbers-table technique). id 는 auto-increment (login 은 email 기준이라 id 무관).
INSERT INTO customer (email, name, password, birth, phone_num, verify_expired_at, verification_code, verify, balance, created_date, modified_date)
SELECT
    CONCAT('customer', n, '@qa.test'),
    CONCAT('QaCustomer', n),
    '$2b$10$8P14US/kur6TMqGsC90ro.r8nvQ8uOrD8zLut4XyA2Y9qvGQiYOzq',  -- bcrypt("password")
    DATE '1990-01-01',
    CONCAT('010-0000-', LPAD(n, 4, '0')),
    NOW(),
    'qa-seed',
    1,                                                                  -- verify=true (BIT(1) 1)
    10000000,
    NOW(6),
    NOW(6)
FROM (
    SELECT a.N + b.N * 10 + c.N * 100 + 1 AS n
    FROM (SELECT 0 N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) a
    CROSS JOIN (SELECT 0 N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) b
    CROSS JOIN (SELECT 0 N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) c
) numbers
WHERE n <= 1000;

-- ROLE_CUSTOMER 부여 (customer1..1000 만)
INSERT INTO customer_roles (customer_id, roles)
SELECT id, 'ROLE_CUSTOMER' FROM customer WHERE email REGEXP '^customer[0-9]+@qa\\.test$';

-- ---------------- 초기 잔액 이력 (SAGA 결제 단계의 전제) ----------------
-- CustomerBalanceHistoryService.changeBalance 는 잔액을 customer.balance 컬럼이 아니라
-- customer_balance_history 의 최신 행(findByCustomerIdRecent = max(id)) 에서 읽는다.
-- 이력이 없으면 잔액을 0 으로 간주해 모든 결제가 NOT_ENOUGH_BALANCE 로 실패하고,
-- SAGA 가 PAYMENT_FAILED 로 끝나 재고 차감(StockConsumer)·주문 확정이 아예 실행되지 않는다.
-- 이 경우 k6 는 HTTP 200(PENDING 생성 성공)만 보므로 에러율 0% 로 보고돼 조용히 지나간다.
--
-- 컬럼 의미 주의 (이름과 반대):
--   change_money  = 결제 "후" 잔액 = 러닝 밸런스   ← 검증이 읽는 값
--   current_money = 결제 "전" 잔액
INSERT INTO customer_balance_history
    (CUSTOMER_ID, change_money, current_money, from_message, description, created_date, modified_date)
SELECT id, 10000000, 0, 'qa-seed', 'QA 초기 잔액', NOW(6), NOW(6)
FROM customer WHERE email REGEXP '^customer[0-9]+@qa\\.test$';

-- 검증
SELECT 'seller' AS table_name, COUNT(*) AS cnt FROM seller WHERE id = 1
UNION ALL
SELECT 'customers', COUNT(*) FROM customer WHERE email REGEXP '^customer[0-9]+@qa\\.test$'
UNION ALL
SELECT 'customer_roles', COUNT(*) FROM customer_roles cr JOIN customer c ON cr.customer_id = c.id WHERE c.email REGEXP '^customer[0-9]+@qa\\.test$'
UNION ALL
SELECT 'balance_history', COUNT(*) FROM customer_balance_history bh JOIN customer c ON bh.CUSTOMER_ID = c.id WHERE c.email REGEXP '^customer[0-9]+@qa\\.test$';
