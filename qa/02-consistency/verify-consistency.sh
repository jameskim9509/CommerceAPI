#!/usr/bin/env bash
# =============================================================================
# ADR-008 §검증 — 교차 DB 3대 불변식 / 9개 체크 (정착 후 최종 DB 상태로 판정)
#
# 런 스코프는 시드 명명 규칙: orders.username LIKE 'ctrich%', customer.email 동일.
# ① 멱등성 위반은 SQL 에 안 잡히므로 k6 summary 의 duplicate_order_responses 를 함께 집계(ADR-008 §측정).
#
# 출력: 9개 체크의 위반 수(행/단위/원) + 총수 N(집계 KPI) + 돈 보존 누수(원). results/<RUN_LABEL>-verify.txt.
#   PASS/FAIL 로 합·불을 찍지 않는다 — 값 그대로 보고하고, arm 간 비교(N(control) → r(treatment))는
#   aggregate-runs.sh 가 중앙값·범위로 한다 (ADR-008 §재현 하네스 4 점추정 금지).
#
# ★ T4 거짓 PASS 주의: "outbox 미발행=0"은 sent_at IS NULL 만 세므로 kafka 로그 전소(T4) 시 이미 sent 표기라
#   손실을 놓친다 → T4 는 돈 보존(v3d)·PENDING/PAID 잔여(v3a) 로만 잡히는 은닉 손실이다.
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

# ---- ① 멱등성 위반 (k6 summary) ----
K6="$RESULTS_DIR/${RUN_LABEL}-k6-summary.json"
if [ -f "$K6" ]; then
    v1=$(grep -oE '"duplicate_order_responses"[[:space:]]*:[[:space:]]*[0-9]+' "$K6" | grep -oE '[0-9]+$' | head -1)
    v1=$(n "$v1")
else
    v1="n/a"
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
v1n=$([ "$v1" = "n/a" ] && echo 0 || echo "$v1")
N=$(( v1n + v2a + v2b + v2c + v3a + v3b + v3c + v3e ))

# 수치 우측정렬 (숫자는 ASCII 라 바이트 폭 = 표시 폭)
num() { printf '%9s' "$1"; }

REPORT="$RESULTS_DIR/${RUN_LABEL}-verify.txt"
{
    echo "===== 정합성 검증 ($RUN_LABEL) ====="
    echo "scope: ctrich | rich=$rich_cnt | hot id=$HOT_ID init=$HOT_INIT now=$hot_now version=$hot_version"
    echo "status 분포: $status_dist"
    echo ""
    u1=$([ "$v1" = "n/a" ] && echo "  (k6 summary 없음)" || echo " 건")
    echo "① 멱등성 위반 (duplicate_order_responses)      : $(num "$v1")$u1"
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
} | tee "$REPORT"

# 스크립트 exit code 는 항상 0 (측정 자체는 성공) — 판정하지 않고 수치만 남긴다.
exit 0
