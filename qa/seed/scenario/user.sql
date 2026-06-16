-- =============================================================================
-- 정합성 통합 시나리오 seed - userApi DB (user)
--
-- 목적: 멱등성 + 낙관적 락(초과판매) + SAGA 보상 을 한 부하에서 모두 발화시키기 위한 고객.
--
-- ★ 중요: CustomerBalanceHistoryService 는 잔액을 customer.balance 컬럼이 아니라
--   customer_balance_history 의 "가장 최근 행 change_money" 로 판단한다.
--   따라서 결제가 성공하려면 balance 컬럼뿐 아니라 history 행(change_money=잔액)도 있어야 한다.
--   (기존 load/user.sql 은 history 를 안 만들어 모든 결제가 NOT_ENOUGH_BALANCE 로 실패했었다.)
--
-- 구성:
--   ctrich{1..300}  : balance 10,000,000 + history 10,000,000  → 결제 성공 (재고 경합 주체)
--   ctbroke{1..60}  : balance 500        + history 500         → 1000 원 결제 시 NOT_ENOUGH_BALANCE (SAGA 결제실패 보상)
--   password 평문: "password" (load seed 와 동일한 BCrypt 해시 재사용)
--   verify=1 (이메일 검증 우회), role=ROLE_CUSTOMER
--   seller 는 만들지 않음 — functional/user.sql 의 seller1(id=1) 이 order seed 의 seller_id 를 점유.
-- =============================================================================

USE `user`;

-- 멱등성: 시나리오 자기 행만 정리
DELETE FROM customer_roles           WHERE customer_id IN (SELECT id FROM (SELECT id FROM customer WHERE email REGEXP '^(ctrich|ctbroke)[0-9]+@qa\\.test$') AS t);
DELETE FROM customer_balance_history WHERE CUSTOMER_ID IN (SELECT id FROM (SELECT id FROM customer WHERE email REGEXP '^(ctrich|ctbroke)[0-9]+@qa\\.test$') AS t);
DELETE FROM customer                 WHERE email REGEXP '^(ctrich|ctbroke)[0-9]+@qa\\.test$';

-- 숫자 테이블 (1..1000)
DROP TEMPORARY TABLE IF EXISTS nums;
CREATE TEMPORARY TABLE nums AS
SELECT a.N + b.N * 10 + c.N * 100 + 1 AS n
FROM (SELECT 0 N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) a
CROSS JOIN (SELECT 0 N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) b
CROSS JOIN (SELECT 0 N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) c;

-- ---------------- ctrich (결제 성공 고객) ----------------
INSERT INTO customer (email, name, password, birth, phone_num, verify_expired_at, verification_code, verify, balance, created_date, modified_date)
SELECT CONCAT('ctrich', n, '@qa.test'), CONCAT('CtRich', n),
       '$2b$10$8P14US/kur6TMqGsC90ro.r8nvQ8uOrD8zLut4XyA2Y9qvGQiYOzq',  -- bcrypt("password")
       DATE '1990-01-01', CONCAT('010-2000-', LPAD(n, 4, '0')),
       NOW(), 'qa-seed', 1, 10000000, NOW(6), NOW(6)
FROM nums WHERE n <= 300;

-- 잔액 이력 (가장 최근 행의 change_money = 가용 잔액). 없으면 결제 로직이 0 으로 인식.
INSERT INTO customer_balance_history (change_money, current_money, from_message, description, CUSTOMER_ID, created_date, modified_date)
SELECT 10000000, 10000000, 'qa-seed', 'scenario rich init', id, NOW(6), NOW(6)
FROM customer WHERE email REGEXP '^ctrich[0-9]+@qa\\.test$';

-- ---------------- ctbroke (잔액 부족 → 결제 실패 보상 경로) ----------------
INSERT INTO customer (email, name, password, birth, phone_num, verify_expired_at, verification_code, verify, balance, created_date, modified_date)
SELECT CONCAT('ctbroke', n, '@qa.test'), CONCAT('CtBroke', n),
       '$2b$10$8P14US/kur6TMqGsC90ro.r8nvQ8uOrD8zLut4XyA2Y9qvGQiYOzq',
       DATE '1990-01-01', CONCAT('010-3000-', LPAD(n, 4, '0')),
       NOW(), 'qa-seed', 1, 500, NOW(6), NOW(6)
FROM nums WHERE n <= 60;

INSERT INTO customer_balance_history (change_money, current_money, from_message, description, CUSTOMER_ID, created_date, modified_date)
SELECT 500, 500, 'qa-seed', 'scenario broke init', id, NOW(6), NOW(6)
FROM customer WHERE email REGEXP '^ctbroke[0-9]+@qa\\.test$';

-- ---------------- roles ----------------
INSERT INTO customer_roles (customer_id, roles)
SELECT id, 'ROLE_CUSTOMER' FROM customer WHERE email REGEXP '^(ctrich|ctbroke)[0-9]+@qa\\.test$';

DROP TEMPORARY TABLE IF EXISTS nums;

SELECT 'ctrich'  AS grp, COUNT(*) AS cnt FROM customer WHERE email REGEXP '^ctrich[0-9]+@qa\\.test$'
UNION ALL
SELECT 'ctbroke', COUNT(*) FROM customer WHERE email REGEXP '^ctbroke[0-9]+@qa\\.test$';
