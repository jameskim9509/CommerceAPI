-- =============================================================================
-- ADR-005 시나리오 3 QA 시드 - userApi DB (user)
-- 이메일 검증 (mailgun) 단계를 우회하기 위해 verify=true 로 직접 INSERT.
--
-- 1000 명의 customer:
--   email: customer{i}@qa.test (i=1..1000)
--   password: "password" (BCrypt $2b$10$... — bcrypt 4.x 생성, Spring BCryptPasswordEncoder 호환)
--   verify: TRUE (검증 우회)
--   balance: 10_000_000 (충분한 잔액)
--   role: CUSTOMER
--
-- 1 명의 seller (상품 등록용):
--   email: seller@qa.test
--   role: SELLER
-- =============================================================================

-- 기존 시드 데이터 정리 (FK 순서 주의)
DELETE FROM customer_roles WHERE customer_id IN (SELECT id FROM (SELECT id FROM customer WHERE email LIKE '%@qa.test') AS t);
DELETE FROM customer_balance_history WHERE CUSTOMER_ID IN (SELECT id FROM (SELECT id FROM customer WHERE email LIKE '%@qa.test') AS t);
DELETE FROM customer WHERE email LIKE '%@qa.test';
DELETE FROM seller_roles WHERE seller_id IN (SELECT id FROM (SELECT id FROM seller WHERE email LIKE '%@qa.test') AS t);
DELETE FROM seller WHERE email LIKE '%@qa.test';

-- AUTO_INCREMENT 리셋 (반복 시드 시 ID 안정화)
ALTER TABLE customer AUTO_INCREMENT = 1;
ALTER TABLE seller AUTO_INCREMENT = 1;

-- 1000 customers (numbers-table technique)
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

-- ROLE_CUSTOMER 부여
INSERT INTO customer_roles (customer_id, roles)
SELECT id, 'ROLE_CUSTOMER' FROM customer WHERE email LIKE 'customer%@qa.test';

-- 단일 seller (상품 등록 주체)
INSERT INTO seller (email, name, password, birth, phone_num, verify_expired_at, verification_code, verify, created_date, modified_date)
VALUES (
    'seller@qa.test',
    'QaSeller',
    '$2b$10$8P14US/kur6TMqGsC90ro.r8nvQ8uOrD8zLut4XyA2Y9qvGQiYOzq',
    DATE '1980-01-01',
    '010-9999-9999',
    NOW(),
    'qa-seed',
    1,
    NOW(6),
    NOW(6)
);

INSERT INTO seller_roles (seller_id, roles)
SELECT id, 'ROLE_SELLER' FROM seller WHERE email = 'seller@qa.test';

-- 검증
SELECT 'customers' AS table_name, COUNT(*) AS cnt FROM customer WHERE email LIKE '%@qa.test'
UNION ALL
SELECT 'customer_roles', COUNT(*) FROM customer_roles cr JOIN customer c ON cr.customer_id = c.id WHERE c.email LIKE '%@qa.test'
UNION ALL
SELECT 'sellers', COUNT(*) FROM seller WHERE email LIKE '%@qa.test';
