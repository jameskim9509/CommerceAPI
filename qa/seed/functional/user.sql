-- =============================================================================
-- 기능/시나리오 QA seed (userApi / user DB) — 모든 환경에 자동 주입되는 "베이스 픽스처"
--   - 비밀번호 평문: password1!  /  BCrypt cost10 hash 아래 사용
--   - mailgun 실패 환경이므로 verify=TRUE / verification_code=NULL 로 검증 완료 상태
--   - roles 컬럼 값은 Authority enum 의 getRole() == "ROLE_CUSTOMER" / "ROLE_SELLER"
--
-- load(k6) seed 와 한 DB 에서 공존하기 위한 규칙:
--   - seller 는 id 1·2 를 "명시적으로" 점유한다. orderApi product.seller_id=1/2 및
--     load/order.sql 의 seller_id=1 이 이 PK 를 그대로 참조한다 (functional 이 항상 먼저 주입됨).
--   - customer 는 9001+ 고 ID 를 쓴다. load/user.sql 의 customer1..1000 과 절대 겹치지 않게.
--   - cleanup 은 "자기 행"만 (customer-%, seller1/2) — load seed 의 customer<digits> 는 건드리지 않는다.
-- =============================================================================

USE `user`;

-- 멱등성: functional 자기 행만 정리 (load 의 customer1..1000@qa.test 는 보존)
DELETE FROM customer_roles           WHERE customer_id IN (SELECT id FROM (SELECT id FROM customer WHERE email LIKE 'customer-%@qa.test') AS t);
DELETE FROM customer_balance_history WHERE CUSTOMER_ID IN (SELECT id FROM (SELECT id FROM customer WHERE email LIKE 'customer-%@qa.test') AS t);
DELETE FROM customer                 WHERE email LIKE 'customer-%@qa.test';
DELETE FROM seller_roles             WHERE seller_id IN (SELECT id FROM (SELECT id FROM seller WHERE email IN ('seller1@qa.test','seller2@qa.test')) AS t);
DELETE FROM seller                   WHERE email IN ('seller1@qa.test','seller2@qa.test');

-- ---------------- SELLERS (명시적 id 1·2) ----------------
INSERT INTO seller
    (id, email, name, password, birth, phone_num, verify_expired_at, verification_code, verify, created_date, modified_date)
VALUES
    (1, 'seller1@qa.test', 'QA Seller 1',
     '$2b$10$IR3iJ.0INCxQFnnjyQcKfOIiWtnArIsx2NN1J7VyV0is2BoTUly2G',
     '1990-01-01', '010-0000-0001', NOW() + INTERVAL 1 YEAR, NULL, b'1', NOW(), NOW()),
    (2, 'seller2@qa.test', 'QA Seller 2',
     '$2b$10$IR3iJ.0INCxQFnnjyQcKfOIiWtnArIsx2NN1J7VyV0is2BoTUly2G',
     '1991-02-02', '010-0000-0002', NOW() + INTERVAL 1 YEAR, NULL, b'1', NOW(), NOW());

INSERT INTO seller_roles (seller_id, roles) VALUES
    (1, 'ROLE_SELLER'),
    (2, 'ROLE_SELLER');

-- ---------------- CUSTOMERS (명시적 id 9001+) ----------------
-- rich:  잔액 충분 (2,000,000) / poor: 잔액 부족 (1,000) / zero: 0원
INSERT INTO customer
    (id, email, name, password, birth, phone_num, verify_expired_at, verification_code, verify, balance, created_date, modified_date)
VALUES
    (9001, 'customer-rich@qa.test', 'QA Rich',
     '$2b$10$IR3iJ.0INCxQFnnjyQcKfOIiWtnArIsx2NN1J7VyV0is2BoTUly2G',
     '1995-05-05', '010-1000-0001', NOW() + INTERVAL 1 YEAR, NULL, b'1', 2000000, NOW(), NOW()),
    (9002, 'customer-poor@qa.test', 'QA Poor',
     '$2b$10$IR3iJ.0INCxQFnnjyQcKfOIiWtnArIsx2NN1J7VyV0is2BoTUly2G',
     '1995-06-06', '010-1000-0002', NOW() + INTERVAL 1 YEAR, NULL, b'1', 1000, NOW(), NOW()),
    (9003, 'customer-zero@qa.test', 'QA Zero',
     '$2b$10$IR3iJ.0INCxQFnnjyQcKfOIiWtnArIsx2NN1J7VyV0is2BoTUly2G',
     '1995-07-07', '010-1000-0003', NOW() + INTERVAL 1 YEAR, NULL, b'1', 0, NOW(), NOW());

INSERT INTO customer_roles (customer_id, roles) VALUES
    (9001, 'ROLE_CUSTOMER'),
    (9002, 'ROLE_CUSTOMER'),
    (9003, 'ROLE_CUSTOMER');

-- 잔액 충전 이력 (rich / poor 만 의미 있음)
INSERT INTO customer_balance_history
    (change_money, current_money, from_message, description, CUSTOMER_ID, created_date, modified_date)
VALUES
    (2000000, 2000000, 'qa-seed', 'initial seed charge', 9001, NOW(), NOW()),
    (1000,    1000,    'qa-seed', 'initial poor seed',   9002, NOW(), NOW());

SELECT '--- FUNCTIONAL USER SEED DONE ---' AS msg, COUNT(*) AS sellers   FROM seller   WHERE email IN ('seller1@qa.test','seller2@qa.test');
SELECT '---'                              AS msg, COUNT(*) AS customers FROM customer WHERE email LIKE 'customer-%@qa.test';
