-- =============================================================================
-- orderApi V6: orders 에 진입 멱등키 기록 (계측용)
--
--  ★ UNIQUE 가 아니라 일반 인덱스다. 유니크 제약을 걸면 DB 가 중복 INSERT 를 거부하게 되어
--    그 자체가 "방어"가 된다. 그러면 control(무방어) arm 에서도 중복이 막혀 arm 비교가 오염된다.
--    이 컬럼의 목적은 차단이 아니라 "같은 키로 몇 건이 만들어졌나"를 사후에 세는 것이다.
--
--  ADR-008 ① 멱등성 위반은 지금까지 "생성 주문 − 의도한 주문" 차분으로만 추정했는데,
--  실패해서 안 만들어진 주문이 중복분을 상쇄해 네 런 모두 0 으로 묻혔다(실측 C-run2:
--  생성 79,624 − 의도 84,098 = −4,474). 이 컬럼이 있으면 키 단위로 정확히 계수된다:
--    SELECT SUM(c-1) FROM (SELECT COUNT(*) c FROM orders
--                          WHERE idempotency_key IS NOT NULL GROUP BY idempotency_key
--                          HAVING COUNT(*) > 1) t;
--
--  NULL 허용: 멱등키 없이 들어온 기존 주문과, 헤더를 생략한 호출을 그대로 수용한다.
-- =============================================================================

ALTER TABLE orders ADD COLUMN idempotency_key VARCHAR(64) NULL;

CREATE INDEX idx_orders_idempotency_key ON orders (idempotency_key);
