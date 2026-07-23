-- =============================================================================
-- 상시 테스트 환경 공통 베이스 픽스처 (userApi / user DB)
--   docker-compose.test.yml · k8s/overlays/test 가 이 파일을 단일 출처로 공유한다
--   (각 환경의 db-seed 가 Flyway 테이블 생성 후 자동 주입). 목적: 통합테스트·수동 확인
--   환경이 항상 "같은 세상"으로 부팅되도록 하는 표준 샘플 데이터.
--   - 비밀번호 평문: password1!  /  BCrypt cost10 hash 아래 사용
--   - mailgun 실패 환경이므로 verify=TRUE / verification_code=NULL 로 검증 완료 상태
--   - roles 컬럼 값은 Authority enum 의 getRole() == "ROLE_CUSTOMER" / "ROLE_SELLER"
--   - seller id 1·2 / customer id 9001+ 를 명시적 고정 PK 로 점유 (안정적 참조용).
--   - cleanup 은 자기 행만 (customer-%, seller1/2) — 멱등(DELETE+INSERT) 재주입 안전.
--
-- 주: QA 부하 시나리오(qa/01-load-balancing)는 자기 seed 를 따로 가진 자립 구조라
--     이 파일과 무관하다 (별도 DB · 별도 seller). 여기서 load seed 를 참조하지 않는다.
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
