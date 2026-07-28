#!/usr/bin/env bash
# =============================================================================
# ADR-008 §검증 — 교차 DB 3대 불변식 / 9개 체크 (정착 후 최종 DB 상태로 판정)
#
# 런 스코프는 시드 명명 규칙: orders.username LIKE 'ctrich%', customer.email 동일.
# ① 멱등성 위반은 orders.idempotency_key(V6, UNIQUE 아님) 단위로 정확히 센다 — 같은 키로 2건 이상
#   만들어졌으면 초과분이 위반이다. k6 는 판정 불가(타임아웃 재시도는 양쪽 응답이 status 0).
#
# 출력: 9개 체크의 위반 수(행/단위/원) + 총수 N(집계 KPI) + 돈 보존 누수(원). results/<RUN_LABEL>-verify.txt.
#   PASS/FAIL 로 합·불을 찍지 않는다 — 값 그대로 보고하고, arm 간 비교(N(control) → r(treatment))는
#   aggregate-runs.sh 가 중앙값·범위로 한다 (ADR-008 §재현 하네스 4 점추정 금지).
#
# ★ T4 거짓 PASS 주의: "outbox 미발행=0"은 sent_at IS NULL 만 세므로 kafka 로그 전소(T4) 시 이미 sent 표기라
#   손실을 놓친다 → T4 는 돈 보존(v3d)·PENDING/PAID 잔여(v3a) 로만 잡히는 은닉 손실이다.
#   (아래 "폴트 귀속" 섹션의 a_T2T4 가 이 사각지대를 sent_at NOT NULL ∖ processed_events 로 메운다.)
#
# ---------------------------------------------------------------------------
# [폴트 귀속(참고) 섹션] — 위 9개 체크 뒤에 덧붙는 원인 추적용 계수. 판정하지 않는다.
#   목적: 잔여(N/r)가 "어느 장애에서 왔는지"를 DB 지문으로 개별 귀속한다.
#   ★ N 산식과 aggregate-runs.sh 파싱 라인은 이 섹션과 무관하다 — 건드리지 않았다.
#     새 라인은 `집계 KPI` / `돈 보존 누수(원) = ` 두 토큰을 절대 쓰지 않는다(오매칭 방지).
#
#   주변 장애: a_T1 (CONFIRMED 인데 환불 = 무에서 돈 창조) / a_T3 (= v1 재사용) / a_T2T4 (발행 후 미처리)
#   의도 장애: d1 (= v1 재사용) / d2 (낙관적 락 충돌 환불) / d3 (재고 부족 환불) / d4 (@Version 존재 확인) / d5 (참고값)
#
#   ★ 한계 (있는 척하지 않는다):
#     - a_T1 은 T1 고유 지문이 아니다. 진짜 서명은 "reserveStock(REQUIRES_NEW) 커밋은 성공했는데
#       바깥 tx 의 processed_events 마커 커밋은 실패했고 그 뒤 재배달이 일어났다" 이며, SIGKILL(T1) 은
#       그 원인 중 하나일 뿐이다. T2(바깥 tx 커밋 실패)나 T1 이 유발한 리밸런스로 인한 in-flight 재배달도
#       바이트 동일한 최종 상태를 만든다. 확정 귀속은 FAULTS 절제 실험(T1 단독 vs T2 단독)으로만 가능하다.
#     - T2(mysql-order 다운으로 재시도 10회 후 드롭)와 T4(kafka 로그 전소)는 DB 지문이 동일해
#       a_T2T4 하나로만 잡히고 분리 불가다. 보조로 PENDING/PAID 의 created_date 분(minute) 히스토그램을
#       내보내니 results/<LABEL>-chaos.log 의 주입 시각(T2=진행도 20%, T4=80%)과 대조하거나,
#       FAULTS 환경변수로 절제 실험(예 FAULTS=T2,T3,T1)을 돌려 확정 귀속할 것.
#     - a_T2T4 는 "미배달"뿐 아니라 "배달됐지만 처리 실패"까지 함께 센다. 특히 payment-reverted /
#       payment-failed 는 대상 주문이 CONFIRMED 면 Order.markFailed 가 INVALID_ORDER_STATE_TRANSITION 을
#       던지고 IdempotentEventHandler 의 @Transactional 이 통째 롤백돼 마커가 영영 안 남는다(재시도해도 동일).
#       즉 g_pr/g_pf 증가분에는 a_T1 과 같은 사건이 이중 계상된다 — 보조로 (g_pr − a_T1) 을 같이 낸다.
#     - d4 는 "④ Lost Update 탐지기"가 아니다. treatment 에서는 history 행 추가와 customer.version 증가가
#       같은 트랜잭션이라 기대식 "version = 행수 − 1" 이 항등식이 되어 구조적으로 항상 0 이고,
#       control 에서는 @Version 필드가 없어 version 0 고정이라 "결제가 1회라도 일어난 고객 수"를 센다.
#       따라서 d4 는 @Version 매핑 존재 확인용 참고값으로만 읽어야 한다. ④ 의 실제 탐지는 d4b(원장 체인)로 한다.
#     - d5(이벤트 중복/역순)는 dedup 이 걸러낸 "중복 배달 횟수"가 DB 에 전혀 남지 않아 계수 불가다.
#       processed_events 총 행수만 참고값으로 낸다.
#     - ★ control(v1) arm 주의 (양 모듈 모두 방어가 빠져 있다):
#         · RefundConsumer 본문이 비어 환불 이력 자체가 없다 → d2/d3 는 물론 a_T1·T1 지문·환불 총건수·
#           FAILED(환불완료 표식)까지 전부 구조적 0 이다. "control 에 T1 이 없었다"는 뜻이 아니다.
#         · orderApi/userApi IdempotentEventHandler 가 **양쪽 다** processed_events 를 기록하지 않는다
#           (v1 doHandle = processor.accept 만). → 5개 토픽 전부 "전량 미처리"로 부풀어 a_T2T4 = 발행 총량이
#           되므로 arm 비교 신호가 아니다. 이 스크립트는 p_* 합계가 0 이면 a_T2T4 를 n/a 로 강등한다.
#         · IdempotencyService 진입 게이트 자체가 제거돼 있다 → control 의 v1/a_T3/d1 은 T3 귀속이 아니라
#           의도된 장애 ①(중복 주문) 그 자체다.
#         · Customer 에 @Version 이 없다 → d4 는 위 서술대로 무의미하고, d4b(원장 체인)가 ④ 의 신호가 된다.
#     - outbox_events / processed_events 에는 런 스코프 컬럼(username/email)이 없어 전역 계수다.
#       시드 cleanup 대상도 아니라 down -v 없이 런을 거듭하면 누적된다.
#       ★ control 런과 treatment 런이 같은 named volume 을 공유하면 control 이 남긴 "마커 0" 상태의
#         outbox 행이 이후 treatment 수치까지 영구 오염시킨다 — arm 전환 시 반드시 down -v 할 것.
#
# 사용: COMPOSE_ARGS="-f docker-compose.qa.yml" RUN_LABEL=T-run1 ./verify-consistency.sh
# =============================================================================
set -uo pipefail
export MSYS_NO_PATHCONV=1
cd "$(dirname "$0")"

COMPOSE_ARGS="${COMPOSE_ARGS:--f docker-compose.qa.yml}"
RUN_LABEL="${RUN_LABEL:-unknown}"
RESULTS_DIR="./results"
mkdir -p "$RESULTS_DIR"

HOT_ID=10001
HOT_INIT=1000
RICH_BAL=10000000
SCOPE_O="username LIKE 'ctrich%'"
SCOPE_U="email LIKE 'ctrich%'"

q_order() { docker compose $COMPOSE_ARGS exec -T mysql-order mysql -uroot -proot -N -B orders -e "$1" 2>/dev/null | tr -d '[:space:]'; }
q_user()  { docker compose $COMPOSE_ARGS exec -T mysql-user  mysql -uroot -proot -N -B user   -e "$1" 2>/dev/null | tr -d '[:space:]'; }
n() { case "$1" in ''|*[!0-9-]*) echo 0 ;; *) echo "$1" ;; esac; }
absn() { local v="$1"; [ "$v" -lt 0 ] && echo $(( -v )) || echo "$v"; }

# 폴트 귀속 섹션 전용 — SQL 리터럴에 한글이 들어가는 쿼리는 클라이언트 charset 을 명시해야
# 조용히 0건 매칭이 되지 않는다(서버는 utf8mb4, 스크립트 파일도 UTF-8/LF). 기존 9개 체크는
# 전부 ASCII 쿼리라 위 헬퍼를 그대로 두고 여기만 별도 헬퍼를 쓴다(기존 동작 무변경 보장).
q_order_u() { docker compose $COMPOSE_ARGS exec -T mysql-order mysql --default-character-set=utf8mb4 -uroot -proot -N -B orders -e "$1" 2>/dev/null | tr -d '[:space:]'; }
q_user_u()  { docker compose $COMPOSE_ARGS exec -T mysql-user  mysql --default-character-set=utf8mb4 -uroot -proot -N -B user   -e "$1" 2>/dev/null | tr -d '[:space:]'; }
# 'a|b|c' 형태 단일 스칼라에서 i 번째 필드 (q_* 의 tr 이 공백을 지우므로 구분자는 '|' 로 고정)
fld() { echo "$1" | cut -d'|' -f"$2"; }

# ---- ① 멱등성 위반 (멱등키 단위 정확 계수) ----
# 같은 Idempotency-Key 로 만들어진 주문이 2건 이상이면 초과분이 곧 위반이다(V6 컬럼).
#   컬럼은 UNIQUE 가 아니다 — 제약을 걸면 DB 가 중복을 막아 control arm 이 오염된다(계측 전용).
# 보조로 "생성 − 의도" 차분도 남긴다. 실패분이 상쇄해 하한이 되지만 총량 감각을 준다.
K6="$RESULTS_DIR/${RUN_LABEL}-k6-summary.json"
orders_made=$(n "$(q_order "SELECT COUNT(*) FROM orders WHERE ($SCOPE_O);")")
v1=$(n "$(q_order "SELECT COALESCE(SUM(c-1),0) FROM (SELECT COUNT(*) c FROM orders WHERE idempotency_key IS NOT NULL AND ($SCOPE_O) GROUP BY idempotency_key HAVING COUNT(*) > 1) t;")")
dup_keys=$(n "$(q_order "SELECT COUNT(*) FROM (SELECT idempotency_key FROM orders WHERE idempotency_key IS NOT NULL AND ($SCOPE_O) GROUP BY idempotency_key HAVING COUNT(*) > 1) t;")")
if [ -f "$K6" ]; then
    intended=$(n "$(grep -oE '"total"[[:space:]]*:[[:space:]]*[0-9]+' "$K6" | grep -oE '[0-9]+$' | head -1)")
    v1_delta=$(( orders_made > intended ? orders_made - intended : 0 ))
else
    intended="n/a"; v1_delta="n/a"
fi

# ---- ② 초과판매 (product_item.count 델타) ----
v2a=$(n "$(q_order "SELECT COUNT(*) FROM product_item WHERE count < 0 AND (name LIKE 'CT-HOT%' OR name LIKE 'CtNormal%');")")
hot_now=$(n "$(q_order "SELECT count FROM product_item WHERE id = $HOT_ID;")")
confirmed_hot_qty=$(n "$(q_order "SELECT COALESCE(SUM(oi.count),0) FROM order_items oi JOIN orders o ON oi.order_id=o.id WHERE o.status='CONFIRMED' AND oi.product_item_id=$HOT_ID;")")
stock_delta=$(( HOT_INIT - hot_now ))
v2b=$(absn $(( stock_delta - confirmed_hot_qty )))       # 차감량 ≠ CONFIRMED 수량
v2c=$(( confirmed_hot_qty > HOT_INIT ? confirmed_hot_qty - HOT_INIT : 0 ))  # CONFIRMED > 초기재고

# ---- ③ 결제·재고 정합성 (돈 보존) ----
v3a=$(n "$(q_order "SELECT COUNT(*) FROM orders WHERE status IN ('PENDING','PAID') AND ($SCOPE_O);")")
v3b=$(n "$(q_order "SELECT COUNT(*) FROM outbox_events WHERE sent_at IS NULL;")")
v3c=$(n "$(q_user  "SELECT COUNT(*) FROM outbox_events WHERE sent_at IS NULL;")")
v3e=$(n "$(q_user  "SELECT COUNT(*) FROM customer WHERE balance < 0 AND ($SCOPE_U);")")

rich_cnt=$(n "$(q_user "SELECT COUNT(*) FROM customer WHERE email LIKE 'ctrich%';")")
bal_init=$(( rich_cnt * RICH_BAL ))
bal_now=$(n "$(q_user "SELECT COALESCE(SUM(balance),0) FROM customer WHERE ($SCOPE_U);")")
confirmed_total=$(n "$(q_order "SELECT COALESCE(SUM(total_price),0) FROM orders WHERE status='CONFIRMED' AND ($SCOPE_O);")")
money_leak=$(( (bal_init - bal_now) - confirmed_total ))    # 0 = 돈 보존
money_leak_abs=$(absn "$money_leak")

# ---- 상태 분포 / failure_reason 분포 (참고) ----
status_dist=$(q_order "SELECT status, COUNT(*) FROM orders WHERE ($SCOPE_O) GROUP BY status;" | tr '\n' ' ')
hot_version=$(n "$(q_order "SELECT version FROM product_item WHERE id=$HOT_ID;")")

# ---- 집계 N (행/단위 위반 총수; ① 포함) ----
# ★ v2b + v2c 를 더하면 초과판매가 이중 계상된다 — 재고가 0 으로 클램프되는 조건에서
#   v2b = |차감량 − CONFIRMED수량| = |init − 0 − confirmed| = confirmed − init = v2c 가 구조적 항등이 된다.
#   실측: control 10/10 런 전부 v2b == v2c (91/108/105/98/110/109/122/115/113/120).
#   treatment 는 v2c=0 이라 영향이 없으므로 이 이중 계상은 control 만 부풀려 arm 배율을 편향시킨다
#   (실측 배율 4.17 → 보정 후 3.61).
#   둘은 같은 초과판매를 다른 각도로 본 것이므로 max 로 합친다 — 두 신호를 다 살리되 한 번만 센다.
#   (v2a·v2b·v2c 개별값은 위 출력에 그대로 남으므로 정보 손실은 없다.)
v1n=$([ "$v1" = "n/a" ] && echo 0 || echo "$v1")
v2max=$(( v2b > v2c ? v2b : v2c ))
N=$(( v1n + v2a + v2max + v3a + v3b + v3c + v3e ))

# =============================================================================
# 폴트 귀속(참고) — 여기부터는 N 에 절대 더하지 않는다. 판정도 하지 않는다.
# =============================================================================

# ---- a_T1: CONFIRMED 인데 환불된 주문 (재고예약 커밋 ↔ 마커 커밋 창 파열; T1 이 주 원인) ----
# ★ 이것은 T1 고유 지문이 아니다. 서명의 실체는 "reserveStock(REQUIRES_NEW)은 커밋됐는데 바깥 tx 의
#   processed_events 마커는 커밋되지 못했고 그 뒤 재배달이 일어났다" 이며, SIGKILL(T1) 외에
#   T2(바깥 tx 커밋 실패)·리밸런스에 의한 in-flight 재배달도 동일한 최종 상태를 만든다.
#   확정 귀속은 FAULTS 절제 실험(FAULTS=T1 단독 vs FAULTS=T2 단독)으로만 가능하다.
# 환불 사실은 orders 에 흔적이 없다(Order 는 @Audited 아님, markFailed 는 CONFIRMED 면 예외로 아무것도 안 씀).
# 유일한 증거가 user DB 의 customer_balance_history.description:
#   RefundConsumer.java:45  "주문 실패 환불 (orderId=" + orderId + ", reason=" + reason + ")"
# → orderId 를 문자열에서 뽑아 orders DB 로 넘긴다(교차 DB라 한 쿼리 불가).
# 비용: description 에 인덱스가 없어 customer_balance_history 풀스캔 1회(수십만 행 규모, 수 초).
#       orders 쪽은 PK IN 이라 range scan.
REFUND_ID_CAP=20000                       # 총 처리 상한(비용 가드). argv 한계는 아래 CHUNK 분할로 회피한다.
# ★ IN 절을 argv 로 통째로 넘기면 Windows CreateProcess 의 32,767자 한계에 걸린다(실측: 6자리 id 약 4,600개
#   ≈ 32,767자에서 `docker: Argument list too long`). q_order 의 2>/dev/null + n() 이 그 실패를 0(=위반 없음)
#   으로 삼켜 "측정 성공, 위반 0" 처럼 보이므로, 개수를 쪼개 부분합을 누적하고 조각 실패 시 n/a 로 강등한다.
REFUND_ID_CHUNK=1000                      # IN 절 1회당 id 개수 (7자리 id 기준 약 8KB — 한계의 1/4)
REFUND_ARG_MAX=20000                      # 조각 문자열 바이트 상한(2차 방어). 이보다 길어지면 즉시 조각을 끊는다.
RX_OID="CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(h.description,'orderId=',-1),',',1) AS UNSIGNED)"
CBH_SCOPE="FROM customer_balance_history h JOIN customer c ON c.id = h.CUSTOMER_ID WHERE c.email LIKE 'ctrich%'"

refund_all=$(n "$(q_user_u "SELECT COUNT(*) $CBH_SCOPE AND h.description LIKE '주문 실패 환불 %';")")
refund_ids=$(n "$(q_user_u "SELECT COUNT(DISTINCT $RX_OID) $CBH_SCOPE AND h.description LIKE '주문 실패 환불 (orderId=%';")")
# T1 지문 그 자체(환불 사유가 '허용되지 않은 주문 상태 전이' = 이미 CONFIRMED 인 주문에 markPaid 재실행)
a_t1_sig=$(n "$(q_user_u "SELECT COUNT(*) $CBH_SCOPE AND h.description LIKE '%reason=허용되지 않은 주문 상태 전이입니다.%';")")

a_t1="0"; a_t1_note=""
if [ "$refund_ids" -gt "$REFUND_ID_CAP" ]; then
    a_t1="n/a"; a_t1_note="orderId 목록 ${refund_ids}개 > 상한 ${REFUND_ID_CAP} — 측정불가"
elif [ "$refund_ids" -gt 0 ]; then
    oid_list=$(q_user_u "SET SESSION group_concat_max_len=16777216; SELECT COALESCE(GROUP_CONCAT(DISTINCT $RX_OID),'') $CBH_SCOPE AND h.description LIKE '주문 실패 환불 (orderId=%';")
    case "$oid_list" in
        ''|*[!0-9,]*) a_t1="n/a"; a_t1_note="orderId 추출 실패(목록 비정상) — 측정불가" ;;
        *)
            # REFUND_ID_CHUNK 개(또는 REFUND_ARG_MAX 바이트)씩 끊어 부분합을 누적한다.
            # 조각 응답이 하나라도 비숫자(= 쿼리/argv 실패)면 합계를 n/a 로 강등해
            # '쿼리 실패'와 '위반 0' 을 구분한다 — 이 하네스의 최대 함정(거짓 클린)을 막는 지점이다.
            a_t1_sum=0; a_t1_fail=0; oid_chunk=""; oid_chunk_n=0
            for _oid in $(echo "$oid_list" | tr ',' ' '); do
                oid_chunk="${oid_chunk:+$oid_chunk,}$_oid"
                oid_chunk_n=$(( oid_chunk_n + 1 ))
                if [ "$oid_chunk_n" -ge "$REFUND_ID_CHUNK" ] || [ "${#oid_chunk}" -ge "$REFUND_ARG_MAX" ]; then
                    _r=$(q_order "SELECT COUNT(*) FROM orders WHERE id IN ($oid_chunk) AND status='CONFIRMED' AND ($SCOPE_O);")
                    case "$_r" in ''|*[!0-9]*) a_t1_fail=1 ;; *) a_t1_sum=$(( a_t1_sum + _r )) ;; esac
                    oid_chunk=""; oid_chunk_n=0
                fi
            done
            if [ -n "$oid_chunk" ]; then
                _r=$(q_order "SELECT COUNT(*) FROM orders WHERE id IN ($oid_chunk) AND status='CONFIRMED' AND ($SCOPE_O);")
                case "$_r" in ''|*[!0-9]*) a_t1_fail=1 ;; *) a_t1_sum=$(( a_t1_sum + _r )) ;; esac
            fi
            if [ "$a_t1_fail" -ne 0 ]; then
                a_t1="n/a"; a_t1_note="orders 조회 조각 실패(응답 비숫자) — 측정불가"
            else
                a_t1="$a_t1_sum"; a_t1_note="orderId ${refund_ids}개, ${REFUND_ID_CHUNK}개씩 분할 조회"
            fi
            ;;
    esac
fi
# 보조: 환불이 정상 착지(= PaymentReverted 로 FAILED 전이)한 주문 수와 그 차분.
# OrderStateConsumer.java:55 의 상수 failure_reason 이 유일한 '환불 완료' 표식이다(원인은 여기서 소실).
failed_reverted=$(n "$(q_order_u "SELECT COUNT(*) FROM orders WHERE status='FAILED' AND failure_reason='재고 차감 실패 후 환불 완료' AND ($SCOPE_O);")")
refund_gap=$(( refund_all - failed_reverted ))

# ---- a_T3: 같은 멱등키로 2건 이상 = v1 재사용 (재계산 안 함) ----
# ★ 'T3 귀속'은 treatment 의 주 경로일 뿐 유일 경로가 아니다:
#   - IdempotencyService 는 SETNX(IN_PROGRESS_TTL=2분) 획득 → action.get() → cache(COMPLETED) 가 비원자다.
#     이 구간이 2분을 넘기거나 그 사이 프로세스가 죽으면 키가 만료되고, 이후 같은 키 재전송이 SETNX 를
#     다시 획득해 두 번째 주문을 만든다(만료 후에는 resolveExisting 의 409 경로에 도달하지 않는다).
#   - control(v1) 은 진입 게이트 자체가 제거돼 있어 이 값은 T3 가 아니라 의도된 장애 ① 그 자체다.
a_t3="$v1n"

# ---- a_T2T4: 발행됐으나(sent_at NOT NULL) 어떤 컨슈머도 처리하지 않은 이벤트 ----
# 토픽→컨슈머 매핑 (SagaTopics.java + 각 @KafkaListener 의 CONSUMER_NAME):
#   order-created            (orders DB 발행) → userapi-payment            (user   DB processed_events)
#   stock-reservation-failed (orders DB 발행) → userapi-refund             (user   DB)
#   payment-deducted         (user   DB 발행) → orderapi-stock             (orders DB)
#   payment-failed           (user   DB 발행) → orderapi-payment-failed    (orders DB)
#   payment-reverted         (user   DB 발행) → orderapi-payment-reverted  (orders DB)
# 5쌍 전부 교차 DB다. event_id 는 outbox 의 PK, (event_id,consumer_name) 은 processed_events 의 PK 라
# 집합 차 |sent| − |processed| 를 건수 차분으로 계산해도 동치다 → 거대한 IN 절 없이 테이블당 1회 스캔.
# ★ outbox_events/processed_events 에는 런 스코프 컬럼이 없어 전역 계수다(시드 cleanup 대상도 아님 → 런 누적).
# 비용: topic/consumer_name 에 인덱스가 없어 풀스캔이지만 DB당 1회로 묶었다(CONCAT_WS 단일 스칼라).
o_sent=$(q_order "SELECT CONCAT_WS('|', COALESCE(SUM(topic='order-created' AND sent_at IS NOT NULL),0), COALESCE(SUM(topic='stock-reservation-failed' AND sent_at IS NOT NULL),0)) FROM outbox_events;")
u_sent=$(q_user  "SELECT CONCAT_WS('|', COALESCE(SUM(topic='payment-deducted' AND sent_at IS NOT NULL),0), COALESCE(SUM(topic='payment-failed' AND sent_at IS NOT NULL),0), COALESCE(SUM(topic='payment-reverted' AND sent_at IS NOT NULL),0)) FROM outbox_events;")
u_proc=$(q_user  "SELECT CONCAT_WS('|', COALESCE(SUM(consumer_name='userapi-payment'),0), COALESCE(SUM(consumer_name='userapi-refund'),0)) FROM processed_events;")
o_proc=$(q_order "SELECT CONCAT_WS('|', COALESCE(SUM(consumer_name='orderapi-stock'),0), COALESCE(SUM(consumer_name='orderapi-payment-failed'),0), COALESCE(SUM(consumer_name='orderapi-payment-reverted'),0)) FROM processed_events;")

s_oc=$(n "$(fld "$o_sent" 1)"); s_srf=$(n "$(fld "$o_sent" 2)")
s_pd=$(n "$(fld "$u_sent" 1)"); s_pf=$(n  "$(fld "$u_sent" 2)"); s_pr=$(n "$(fld "$u_sent" 3)")
p_oc=$(n "$(fld "$u_proc" 1)"); p_srf=$(n "$(fld "$u_proc" 2)")
p_pd=$(n "$(fld "$o_proc" 1)"); p_pf=$(n  "$(fld "$o_proc" 2)"); p_pr=$(n "$(fld "$o_proc" 3)")

g_oc=$((  s_oc  > p_oc  ? s_oc  - p_oc  : 0 ))
g_srf=$(( s_srf > p_srf ? s_srf - p_srf : 0 ))
g_pd=$((  s_pd  > p_pd  ? s_pd  - p_pd  : 0 ))
g_pf=$((  s_pf  > p_pf  ? s_pf  - p_pf  : 0 ))
g_pr=$((  s_pr  > p_pr  ? s_pr  - p_pr  : 0 ))

# ★ control(v1) 강등: 양 모듈 IdempotentEventHandler 가 processed_events 를 아예 기록하지 않으므로
#   p_* 가 전부 0 이 되고 g_* = s_* 즉 "발행 총량"이 된다. 숫자를 그대로 내면 100% 오독되므로 n/a 로 강등한다.
s_total=$(( s_oc + s_srf + s_pd + s_pf + s_pr ))
p_total=$(( p_oc + p_srf + p_pd + p_pf + p_pr ))
if [ "$p_total" -eq 0 ] && [ "$s_total" -gt 0 ]; then
    a_t2t4="n/a"
    a_t2t4_note="processed_events 전무(control/v1 = 양 모듈 dedup 미기록) → 발행 총량 $s_total 이 그대로 잡힘 — 측정불가"
else
    a_t2t4=$(( g_oc + g_srf + g_pd + g_pf + g_pr ))
    a_t2t4_note="sent_at NOT NULL 인데 processed_events 없음, 전역"
fi

# g_pr / g_pf 에는 T1 과 같은 사건(CONFIRMED 주문에 markFailed → 예외 → 마커 미저장 → 재시도해도 동일)이
# 섞여 a_T1 과 이중 계상된다. 참고용으로 T1 추정분을 뺀 잔여를 따로 낸다.
if [ "$a_t1" = "n/a" ]; then
    g_pr_ex="n/a"
else
    g_pr_ex=$(( g_pr > a_t1 ? g_pr - a_t1 : 0 ))
fi

# ---- o_L1/o_L2/o_L3: outbox_events 만으로 세는 SAGA 체인 보존식 (★ arm 중립) ----
# a_T2T4 는 processed_events 를 기준선으로 쓰는데 control(v1) 은 그 기록 자체를 제거해(⑤ 방어 제거)
# n/a 로 강등된다 — 계측기가 방어 그 자체라 arm 비교가 불가능하다.
# v1 이 건드리지 않는 outbox_events 만으로 같은 손실을 세면 양 arm 에서 같은 의미를 갖는다.
#
# SAGA 체인(ADR-003) — 각 hop 은 반드시 하나의 후속을 낳는다:
#   OrderCreated           → PaymentConsumer → PaymentDeducted | PaymentFailed
#   PaymentDeducted        → StockConsumer   → 주문 CONFIRMED  | StockReservationFailed
#   StockReservationFailed → RefundConsumer  → PaymentReverted        (★ control 은 본문이 비어 미발행)
#
# ★ 부호를 살린다 (clamp 하지 않는다):
#     양수 = 하류가 못 받음 → 전송 유실(T2 드롭 / T4 로그 전소)
#     음수 = 같은 이벤트가 두 번 처리됨 → ⑤ 이벤트 중복 배달 위반(control 기대 신호)
#   ⑤ 는 지금까지 "계수 불가"였다(dedup 이 걸러낸 재배달은 DB 에 흔적이 없다). control 은 dedup 이 없어
#   재배달이 곧 재실행이고 그때마다 후속 이벤트를 한 번 더 발행하므로, o_L1 의 음수 크기가 ⑤ 의 하한이 된다.
#
# ★ 정착(quiescence) 이후에 읽어야 한다 — 부하 중에는 in-flight 가 그대로 양수로 잡힌다
#   (실측: 부하 중간 o_L1=907 인데 그때 PENDING/PAID 가 1,369 였다).
# ★ o_L2 는 중복 처리에 강건하다: 이미 CONFIRMED 인 주문에 PaymentDeducted 가 재배달되면 markPaid 가
#   예외를 던져 StockReservationFailed 가 발행되므로 s_pd = confirmed + s_srf 항등이 유지된다.
# ★ o_L3 만 arm 중립이 아니다 — control 은 RefundConsumer 본문이 비어 s_pr=0 이라 o_L3 = s_srf 가 된다.
#   control 에서는 "환불 미실행 건수"로 읽고, 유실 지표로 읽지 말 것.
# ★ outbox 는 전역 계수, orders 는 런 스코프(ctrich)다. 런마다 down -v 하는 전용 스택에서만 일치한다.
confirmed_cnt=$(n "$(q_order "SELECT COUNT(*) FROM orders WHERE status='CONFIRMED' AND ($SCOPE_O);")")
o_l1=$(( s_oc  - s_pd - s_pf ))
o_l2=$(( s_pd  - confirmed_cnt - s_srf ))
o_l3=$(( s_srf - s_pr ))

# T2/T4 분리 보조: 미착지 주문(PENDING/PAID)의 생성 시각 분 단위 히스토그램.
# 카오스 로그의 주입 시각과 대조해 클러스터 위치로 귀속한다(T2=진행도 20%, T4=80%).
pend_hist=$(q_order "SET SESSION group_concat_max_len=1048576; SELECT COALESCE(GROUP_CONCAT(CONCAT(m,'=',c) ORDER BY m SEPARATOR '|'),'-') FROM (SELECT DATE_FORMAT(created_date,'%H:%i') m, COUNT(*) c FROM orders WHERE status IN ('PENDING','PAID') AND ($SCOPE_O) GROUP BY 1) t;")
[ -z "$pend_hist" ] && pend_hist="-"

# ---- d1: 중복 주문/결제 = v1 재사용 ----
d1="$v1n"

# ---- d2 / d3: 환불 사유별 귀속 (orders 로는 구분 불가 — ADR-008 이 지목한 지점) ----
# orders.failure_reason 은 PaymentReverted 경로에서 상수 '재고 차감 실패 후 환불 완료' 로 덮여 원인이 소실된다.
# 원인은 오직 user DB 의 balance history description 안 'reason=' 뒤에만 남는다.
#   d2 ← StockConsumer.java:49 ObjectOptimisticLockingFailureException → reason "재고 동시성 충돌"
#   d3 ← ErrorCode.NOT_ENOUGH_ITEM_COUNT "상품 아이템의 수량이 부족합니다."
# ★ control(v1) 은 RefundConsumer 본문이 비어 환불 이력 자체가 생기지 않으므로 d2/d3 는 항상 0 이다.
d2=$(n "$(q_user_u "SELECT COUNT(*) $CBH_SCOPE AND h.description LIKE '%reason=재고 동시성 충돌%';")")
d3=$(n "$(q_user_u "SELECT COUNT(*) $CBH_SCOPE AND h.description LIKE '%reason=상품 아이템의 수량이 부족합니다.%';")")

# ---- d4: @Version 방어 존재 확인용 (★ Lost Update 탐지기가 아니다) ----
# 기대식: changeBalance 1회 = history 행 +1 && customer.setBalance 로 dirty update = version +1.
#   시드는 history 1행을 raw SQL 로 넣고 customer.version=0 이므로 기대 version = (행수 − 1).
# ★ 이 기대식은 구현·시드와 맞지만, 그것이 깨지는 사건이 Lost Update 가 아니다:
#   - treatment: save(history) 와 setBalance(→version+1) 가 같은 트랜잭션(CustomerBalanceHistoryService)이라
#     커밋/롤백이 항상 함께다. 낙관적 락 충돌 시에도 tx 통째 롤백 후 재시도라 둘이 어긋날 수 없다 → d4 ≡ 0.
#   - control(v1): @Version 필드 자체가 없어 version 0 고정 → 행수≠1 인 모든 고객, 즉 "결제가 한 번이라도
#     성공한 고객 수"(부하 후 ≒ rich_cnt)를 센다. 활동 없는 고객은 행수=1 이라 일치하므로 '전 고객'은 아니다.
#   따라서 d4 는 "@Version 매핑이 살아 있는가"만 알려주는 참고값이다. ④ 의 실제 탐지는 아래 d4b 로 한다.
# 비용: customer(300행) LEFT JOIN customer_balance_history 그룹 집계 1회.
d4=$(n "$(q_user "SELECT COUNT(*) FROM (SELECT c.id FROM customer c LEFT JOIN customer_balance_history h ON h.CUSTOMER_ID = c.id WHERE c.email LIKE 'ctrich%' GROUP BY c.id, c.version HAVING c.version <> COUNT(h.id) - 1) t;")")
d4_sample=$(q_user "SET SESSION group_concat_max_len=1048576; SELECT COALESCE(GROUP_CONCAT(CONCAT('(v=',v,',h=',hn,')') SEPARATOR ','),'-') FROM (SELECT c.version v, COUNT(h.id) hn FROM customer c LEFT JOIN customer_balance_history h ON h.CUSTOMER_ID = c.id WHERE c.email LIKE 'ctrich%' GROUP BY c.id, c.version ORDER BY c.id LIMIT 5) t;")
[ -z "$d4_sample" ] && d4_sample="-"

# ---- d4b: ④ 잔액 Lost Update — 원장 체인 끊김 (금액 기반, arm 비교 가능) ----
# CustomerBalanceHistoryService 는 최신행(max id)을 읽어 새 행을 만든다:
#   new.current_money = prev.change_money , new.change_money = prev.change_money + money
# 즉 고객별로 id 오름차순 정렬했을 때 각 행의 current_money 는 직전 행의 change_money 와 반드시 같아야 한다.
# 두 트랜잭션이 같은 '최신행'을 동시에 읽고 각각 커밋하면(=갱신 유실) 이 링크가 끊긴다.
#   control(@Version 없음) > 0 / treatment(충돌 시 롤백·재시도) = 0 이 기대 신호다.
# 참고: 파생 캐시 대조 — 고객별 최신 change_money 합(원장) vs SUM(customer.balance)(캐시).
d4_chain=$(n "$(q_user "SELECT COUNT(*) FROM (SELECT h.current_money cm, LAG(h.change_money) OVER (PARTITION BY h.CUSTOMER_ID ORDER BY h.id) prev FROM customer_balance_history h JOIN customer c ON c.id = h.CUSTOMER_ID WHERE c.email LIKE 'ctrich%') t WHERE t.prev IS NOT NULL AND t.cm <> t.prev;")")
ledger_now=$(n "$(q_user "SELECT COALESCE(SUM(h.change_money),0) FROM customer_balance_history h JOIN (SELECT MAX(h2.id) mid FROM customer_balance_history h2 JOIN customer c2 ON c2.id = h2.CUSTOMER_ID WHERE c2.email LIKE 'ctrich%' GROUP BY h2.CUSTOMER_ID) m ON m.mid = h.id;")")
ledger_gap=$(( ledger_now - bal_now ))

# ---- d5: 이벤트 중복/역순 — 참고값만 ----
# dedup 이 걸러낸 "중복 배달 횟수"는 어디에도 기록되지 않는다(IdempotentEventHandler 는 skip 로그만 남기고 return).
# 따라서 계수 불가. processed_events 총 행수(= 실제 처리된 고유 이벤트 수)만 컨슈머별로 낸다. 전역 계수.
d5_order=$(q_order "SET SESSION group_concat_max_len=1048576; SELECT COALESCE(GROUP_CONCAT(CONCAT(consumer_name,'=',c) ORDER BY consumer_name SEPARATOR '|'),'-') FROM (SELECT consumer_name, COUNT(*) c FROM processed_events GROUP BY consumer_name) t;")
d5_user=$(q_user  "SET SESSION group_concat_max_len=1048576; SELECT COALESCE(GROUP_CONCAT(CONCAT(consumer_name,'=',c) ORDER BY consumer_name SEPARATOR '|'),'-') FROM (SELECT consumer_name, COUNT(*) c FROM processed_events GROUP BY consumer_name) t;")
[ -z "$d5_order" ] && d5_order="-"
[ -z "$d5_user" ]  && d5_user="-"

# 수치 우측정렬 (숫자는 ASCII 라 바이트 폭 = 표시 폭)
num() { printf '%9s' "$1"; }

REPORT="$RESULTS_DIR/${RUN_LABEL}-verify.txt"
{
    echo "===== 정합성 검증 ($RUN_LABEL) ====="
    echo "scope: ctrich | rich=$rich_cnt | hot id=$HOT_ID init=$HOT_INIT now=$hot_now version=$hot_version"
    echo "status 분포: $status_dist"
    echo ""
    echo "① 중복 주문 (같은 멱등키로 2건 이상)          : $(num "$v1") 건  (중복 키 $dup_keys 개, 생성 $orders_made)"
    echo "   └ 참고: 생성 − 의도 차분(하한)              : $(num "$v1_delta") 건  (의도 $intended)"
    echo "② 초과판매"
    echo "   v2a 음수 재고 행                            : $(num "$v2a") 행"
    echo "   v2b |차감량 − CONFIRMED수량|                : $(num "$v2b") 개  (차감량 $stock_delta = init $HOT_INIT − now $hot_now, CONFIRMED $confirmed_hot_qty)"
    echo "   v2c CONFIRMED수량 − 초기재고 초과분         : $(num "$v2c") 개"
    echo "③ 결제·재고 정합성 (돈 보존)"
    echo "   v3a PENDING/PAID 잔여                       : $(num "$v3a") 행"
    echo "   v3b outbox 미발행(orders)                   : $(num "$v3b") 행  ※ T4 는 여기서 0 으로 보인다(은닉 손실은 v3d/v3a 로)"
    echo "   v3c outbox 미발행(user)                     : $(num "$v3c") 행  ※ 동일"
    echo "   v3d 돈 보존 누수                            : $(num "$money_leak") 원  ((초기잔액합 $bal_init − 현재 $bal_now) − CONFIRMED결제 $confirmed_total)"
    echo "   v3e 음수 잔액 행                            : $(num "$v3e") 행"
    echo ""
    echo "집계 KPI(위반 행/단위 총수, ① 포함) N = $N"
    echo "돈 보존 누수(원) = $money_leak  (부호: +면 소실/은닉, −면 무에서 창조)"
    echo ""
    echo "※ 값만 보고한다 — 합·불 판정 없음. 0 = 잔여 없음."
    echo "   arm 비교는 aggregate-runs.sh 의 중앙값·범위로: N(control) → r(treatment)."
    echo "   treatment 의 잔여 r>0 은 5개 방어 대상이 아닌 주변 장애 T1–T4 몫. control 은 desired 위반까지 더해 N≫r."
    echo ""
    echo "----- 폴트 귀속(참고) — 판정 아님, 위 N 에 더하지 않음 -----"
    echo "[주변 장애 T1–T4]"
    echo "   a_T1  CONFIRMED 인데 환불된 주문         : $(num "$a_t1") 건  ${a_t1_note:+($a_t1_note)}"
    echo "         └ T1 지문(reason=허용되지 않은 주문 상태 전이입니다.) : $(num "$a_t1_sig") 행"
    echo "         └ 환불 총건수 $refund_all / FAILED(환불완료 표식) $failed_reverted / 차분 $refund_gap"
    echo "         ※ T1 고유 지문이 아니다 — 실체는 '재고예약(REQUIRES_NEW) 커밋 ↔ 마커 커밋 창 파열 후 재배달'."
    echo "            SIGKILL(T1) 이 주 원인이지만 T2(바깥 tx 커밋 실패)·리밸런스 재배달도 같은 상태를 만든다."
    echo "            확정 귀속은 FAULTS 절제 실험(FAULTS=T1 단독 vs FAULTS=T2 단독)으로만 가능하다."
    echo "         ※ control(v1) 은 RefundConsumer 본문이 비어 환불 이력 자체가 없다 → a_T1·T1 지문·환불 총건수·"
    echo "            FAILED 표식이 전부 구조적 0 이다. 'control 에 무에서 돈 창조가 없었다'는 뜻이 아니며,"
    echo "            control 의 T1 손실은 환불 미실행에 의한 소실로 v3d 양수 쪽에만 나타난다."
    echo "   a_T3  같은 멱등키 초과 생성(= v1 재사용) : $(num "$a_t3") 건  (주 경로는 T3=FLUSHALL)"
    echo "         ※ 유일 경로는 아니다 — IN_PROGRESS TTL 2분 만료 + SETNX↔cache 비원자 창으로도 발생 가능."
    echo "            control(v1) 은 진입 멱등 게이트 자체가 없어 이 값은 T3 가 아니라 의도된 장애 ① 그 자체다."
    echo "   a_T2T4 발행 후 미처리 이벤트             : $(num "$a_t2t4") 건  ($a_t2t4_note)"
    echo "         └ order-created→userapi-payment              $(num "$g_oc") (sent $s_oc / proc $p_oc)"
    echo "         └ stock-reservation-failed→userapi-refund    $(num "$g_srf") (sent $s_srf / proc $p_srf)"
    echo "         └ payment-deducted→orderapi-stock            $(num "$g_pd") (sent $s_pd / proc $p_pd)"
    echo "         └ payment-failed→orderapi-payment-failed     $(num "$g_pf") (sent $s_pf / proc $p_pf)"
    echo "         └ payment-reverted→orderapi-payment-reverted $(num "$g_pr") (sent $s_pr / proc $p_pr)"
    echo "         └ payment-reverted 에서 a_T1 추정분 제외한 잔여      : $(num "$g_pr_ex")"
    echo "         └ PENDING/PAID 생성시각 분포(분): $pend_hist"
    echo "         ※ 정의는 '미배달'이 아니라 '발행됐는데 마커 없음' = 미배달(T2/T4) + 처리실패 합계다."
    echo "            payment-reverted / payment-failed 는 대상 주문이 CONFIRMED 면 Order.markFailed 가 예외를 던져"
    echo "            트랜잭션이 롤백돼 마커가 영영 안 남는다(재시도해도 동일) → g_pr·g_pf 에 a_T1 과 같은 사건이"
    echo "            이중 계상된다. 위 'a_T1 추정분 제외' 잔여를 T2/T4 근사치로 보고, FAULTS=T2,T4(T1 제외)"
    echo "            절제 런에서 g_pr 이 0 으로 떨어지는지로 검증하라."
    echo "         ※ T2(mysql-order 다운→재시도 소진 드롭)와 T4(kafka 로그 전소)는 DB 지문이 같아 이 수치만으론 분리 불가."
    echo "            위 분포의 클러스터 위치를 results/${RUN_LABEL}-chaos.log 의 주입 시각과 대조하라 —"
    echo "            T2 는 부하 진행도 20%, T4 는 80% 지점에 주입되므로 클러스터 위치로 구분한다."
    echo "            확정 귀속이 필요하면 FAULTS 로 절제 실험을 돌릴 것 (예: FAULTS=T2,T3,T1 → T4 제외, FAULTS=T4 → 단독)."
    echo "         ※ control(v1) 은 orderApi·userApi **양쪽 다** processed_events 를 기록하지 않는다(doHandle 이 accept 만)."
    echo "            → 위 5개 토픽 전부가 '전량 미처리'로 부풀어 a_T2T4 = 발행 총량이 된다. arm 비교 신호가 아니며,"
    echo "            이 스크립트는 p_* 합계가 0 이면 a_T2T4 를 n/a 로 강등한다(토픽별 sent/proc 는 원시값 그대로 남긴다)."
    echo "         ※ 전역·누적 계수다 — control 런이 남긴 '마커 0' outbox 행이 같은 named volume 을 쓰는 이후"
    echo "            treatment 런까지 영구 오염시킨다. arm 을 바꿀 때는 반드시 down -v 로 볼륨을 비울 것."
    echo "   [outbox 체인 보존식 — processed_events 비의존, ★ 양 arm 비교 가능]"
    echo "         o_L1 order-created 미착지    : $(num "$o_l1")   (s_oc $s_oc − s_pd $s_pd − s_pf $s_pf)"
    echo "         o_L2 payment-deducted 미착지 : $(num "$o_l2")   (s_pd $s_pd − CONFIRMED $confirmed_cnt − s_srf $s_srf)"
    echo "         o_L3 stock-resv-failed 미착지: $(num "$o_l3")   (s_srf $s_srf − s_pr $s_pr)  ※ arm 중립 아님"
    echo "         ※ 부호가 의미다 — 양수 = 하류가 못 받음(T2 드롭 / T4 로그 전소), 음수 = 같은 이벤트 이중 처리(⑤)."
    echo "            a_T2T4 가 control 에서 n/a 인 이유(계측기 processed_events 가 곧 ⑤ 방어)를 우회한다."
    echo "            v1 은 outbox_events 를 건드리지 않으므로 이 세 값은 양 arm 에서 같은 의미를 갖는다."
    echo "         ※ ⑤ 의 하한: control 은 dedup 이 없어 재배달이 곧 재실행이고 그때마다 후속 이벤트를 한 번 더"
    echo "            발행한다 → o_L1 이 음수면 그 절대값이 '중복 처리된 OrderCreated 수' 하한이다(d5 보완)."
    echo "         ※ 정착 이후 값이라야 유효하다 — 부하 중이면 in-flight 가 그대로 양수로 잡힌다."
    echo "         ※ o_L3 는 control 에서 RefundConsumer 본문이 비어 s_pr=0 → o_L3 = s_srf 가 된다."
    echo "            control 에서는 '환불 미실행 건수'로 읽고 유실 지표로 읽지 말 것."
    echo "[의도된 장애 ①–⑤]"
    echo "   d1    중복 주문/결제 (= v1 재사용)       : $(num "$d1") 건"
    echo "   d2    낙관적 락 충돌 환불                : $(num "$d2") 건  (reason=재고 동시성 충돌)"
    echo "   d3    재고 부족 환불                     : $(num "$d3") 건  (reason=상품 아이템의 수량이 부족합니다.)"
    echo "         ※ d2/d3 구분 정보는 orders.failure_reason 에 남지 않는다(상수로 덮임) — 잔액이력 description 전용."
    echo "            control(v1) 은 RefundConsumer 본문이 비어 환불 이력 자체가 없어 d2/d3 = 0 이다."
    echo "   d4    @Version 존재 확인용(참고)         : $(num "$d4") 명  (기대식: version = history행수 − 1, 시드 1행 제외)"
    echo "         └ 표본 (version, 행수): $d4_sample"
    echo "         ※ ★ Lost Update 탐지력이 없다 — 라벨을 '④ 의심'으로 읽지 말 것."
    echo "            treatment: history 행 추가와 version 증가가 같은 트랜잭션이라 기대식이 항등식 → 구조적으로 0."
    echo "            control(v1): @Version 필드가 없어 version 0 고정 → '결제가 1회라도 성공한 고객 수'를 센다"
    echo "            (활동 없는 고객은 행수=1 이라 일치하므로 '전 고객'은 아니다). 양쪽 다 위반량과 무관하다."
    echo "   d4b   ④ 잔액 Lost Update (원장 체인 끊김) : $(num "$d4_chain") 행  (current_money ≠ 직전 행 change_money)"
    echo "         └ 원장 최신합 $ledger_now vs customer.balance 합 $bal_now / 차 $ledger_gap"
    echo "         ※ 이쪽이 ④ 의 실제 신호다. 같은 '최신행'을 두 트랜잭션이 동시에 읽고 각각 커밋하면 링크가 끊긴다."
    echo "            control > 0 / treatment = 0 이 기대. 차(ledger_gap)≠0 은 파생 캐시(customer.balance)만 덮인 경우다."
    echo "   d5    processed_events 총 행수           : orders[$d5_order] user[$d5_user]"
    echo "         ※ 중복 배달 횟수는 현재 계측 불가 — dedup 이 걸러낸 재배달은 DB 에 흔적을 남기지 않는다."
    echo "            (per-fault 귀속 후속 과제. 위 행수는 '처리된 고유 이벤트 수'일 뿐 중복 횟수가 아니다.)"
    echo "         ※ outbox_events/processed_events 는 런 스코프 컬럼이 없어 전역 계수이며 down -v 전까지 런 간 누적된다."
} | tee "$REPORT"

# 스크립트 exit code 는 항상 0 (측정 자체는 성공) — 판정하지 않고 수치만 남긴다.
exit 0
