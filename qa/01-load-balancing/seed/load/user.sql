-- =============================================================================
-- ADR-005 시나리오 3 부하(k6) 시드 - userApi DB (user)
-- 이메일 검증 (mailgun) 단계를 우회하기 위해 verify=true 로 직접 INSERT.
--
-- 1000 명의 customer:
--   email: customer{i}@qa.test (i=1..1000)  ← load-test.js 가 이 규칙으로 로그인
--   password: "password" (BCrypt — Spring BCryptPasswordEncoder 호환)
--   verify: TRUE (검증 우회) / balance: 10_000_000 / role: CUSTOMER
--
-- seller 는 만들지 않는다 — functional/user.sql 이 seller id=1·2 를 먼저 점유하고,
-- order 시드의 seller_id=1 이 그 PK 를 재사용한다 (functional → load 순서 보장).
-- cleanup 은 customer<digits>@qa.test (자기 행)만 — functional 의 customer-%@qa.test 는 보존.
-- =============================================================================

USE `user`;

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

-- 검증
SELECT 'customers' AS table_name, COUNT(*) AS cnt FROM customer WHERE email REGEXP '^customer[0-9]+@qa\\.test$'
UNION ALL
SELECT 'customer_roles', COUNT(*) FROM customer_roles cr JOIN customer c ON cr.customer_id = c.id WHERE c.email REGEXP '^customer[0-9]+@qa\\.test$';
