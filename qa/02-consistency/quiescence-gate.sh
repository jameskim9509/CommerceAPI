#!/usr/bin/env bash
# =============================================================================
# ADR-008 §재현 하네스 3 — 정착(quiescence) 게이트
#
# 고정 sleep 이 아니라, 비동기 SAGA 흐름(보상 이벤트·아웃박스 발행)이 실제로 멎었는지 폴링으로 확인한다.
# "정착" = 시스템이 더 이상 변하지 않음(stability). 다음이 K회 연속 만족하면 정착으로 판정:
#   1) orders DB outbox 미발행 = 0        (sent_at IS NULL)
#   2) user   DB outbox 미발행 = 0
#   3) orders DB 중간 상태(PENDING/PAID) 스코프 잔여가 직전 라운드와 불변(delta=0)  ★ '=0' 아님
#   4) kafka consumer lag 합 = 0           (best-effort — kafka 재생성(T4) 직후 group 소실은 lag 0 취급)
#
# ★ 조건 3 은 "=0" 이 아니라 "불변" 이다. 주변 장애(T2 이벤트 드롭 / T4 로그 전소)로 SAGA 가 영구 정지해도
#   재구동 스케줄러가 없어 PENDING/PAID 가 0 으로 안 내려간다. 그 수가 '더 이상 안 줄면' 1·2·4 는 이미 0 이므로
#   시스템은 정착한 것 — 잔여를 남긴 채 exit 0 하고 그 잔여는 verify(v3a) 가 r 로 계수한다.
#   즉 '=0' 정합성 판정은 verify 몫으로 넘기고 게이트는 '변화가 멎었나' 만 본다.
#   TIMEOUT 은 이제 "안정화조차 못 함(아직 드레인/변동 중)" 이라는 더 강한 이상 신호다.
#
# 사용: COMPOSE_ARGS="-f docker-compose.qa.yml" ./quiescence-gate.sh
# 환경변수: STABLE_ROUNDS(기본 3) INTERVAL(기본 5) TIMEOUT(기본 600)
# =============================================================================
set -uo pipefail
export MSYS_NO_PATHCONV=1
cd "$(dirname "$0")"

COMPOSE_ARGS="${COMPOSE_ARGS:--f docker-compose.qa.yml}"
STABLE_ROUNDS="${STABLE_ROUNDS:-3}"
INTERVAL="${INTERVAL:-5}"
TIMEOUT="${TIMEOUT:-600}"
SCOPE="username LIKE 'ctrich%'"

# ★ 정착 도달 여부를 산출물로 남긴다 — RUN_LABEL 이 있으면 results/<LABEL>-quiescence.log 에 tee.
#   verify 는 "정착 후 최종 DB 상태"를 전제로 판정하는데, 게이트가 stdout 에만 찍으면
#   사후에 그 전제가 충족됐는지 확인할 방법이 없다(README 절차는 게이트 실패와 무관하게
#   다음 줄에서 verify 를 실행하므로 TIMEOUT 이 나도 verify.txt 는 똑같이 생성된다).
#   측정 산출물 3종(verify/chaos/k6) 옆에 게이트 로그를 나란히 남겨야 런의 유효성을 재검할 수 있다.
RUN_LABEL="${RUN_LABEL:-}"
if [ -n "$RUN_LABEL" ]; then
    mkdir -p ./results
    exec > >(tee "./results/${RUN_LABEL}-quiescence.log") 2>&1
fi

q_order() { docker compose $COMPOSE_ARGS exec -T mysql-order mysql -uroot -proot -N -B orders -e "$1" 2>/dev/null | tr -d '[:space:]'; }
q_user()  { docker compose $COMPOSE_ARGS exec -T mysql-user  mysql -uroot -proot -N -B user   -e "$1" 2>/dev/null | tr -d '[:space:]'; }

kafka_lag() {
    docker compose $COMPOSE_ARGS exec -T kafka \
        kafka-consumer-groups.sh --bootstrap-server localhost:9092 --all-groups --describe 2>/dev/null \
        | awk 'NR>1 && $6 ~ /^[0-9]+$/ {sum+=$6} END{print sum+0}'
}

num() { case "$1" in ''|*[!0-9]*) echo 999999 ;; *) echo "$1" ;; esac; }

echo "[quiescence] 정착 대기 시작 (STABLE_ROUNDS=$STABLE_ROUNDS INTERVAL=${INTERVAL}s TIMEOUT=${TIMEOUT}s)"
start=$(date +%s)
stable=0
prev_pending=-1                 # 첫 라운드는 비교 대상이 없어 불변 판정 불가 → 자연히 stable 미가산
while true; do
    ob_order=$(num "$(q_order 'SELECT COUNT(*) FROM outbox_events WHERE sent_at IS NULL;')")
    ob_user=$(num "$(q_user  'SELECT COUNT(*) FROM outbox_events WHERE sent_at IS NULL;')")
    pending=$(num "$(q_order "SELECT COUNT(*) FROM orders WHERE status IN ('PENDING','PAID') AND ($SCOPE);")")
    lag=$(num "$(kafka_lag)")

    # 정착 = 비동기 작업(1·2·4)이 멎었고(=0) 중간 상태(3)가 직전 라운드와 불변.
    #   pending=999999 는 측정 실패 sentinel(실 pending ≤ ORDER_TARGET) → 불변으로 세지 않는다.
    if [ "$ob_order" = "0" ] && [ "$ob_user" = "0" ] && [ "$lag" = "0" ] \
       && [ "$pending" != "999999" ] && [ "$pending" = "$prev_pending" ]; then
        stable=$((stable + 1))
        echo "[quiescence] quiet round $stable/$STABLE_ROUNDS (outbox o=$ob_order u=$ob_user, pending/paid=$pending 불변, lag=$lag)"
        if [ "$stable" -ge "$STABLE_ROUNDS" ]; then
            echo "[quiescence] 정착 완료 ✓ — ${STABLE_ROUNDS}회 연속 변화 없음 (outbox o=$ob_order u=$ob_user, pending/paid=$pending, lag=$lag)"
            exit 0
        fi
    else
        [ "$stable" -ne 0 ] && echo "[quiescence] reset (outbox o=$ob_order u=$ob_user, pending/paid=$pending prev=$prev_pending, lag=$lag)"
        stable=0
    fi
    prev_pending="$pending"

    now=$(date +%s)
    if [ $((now - start)) -ge "$TIMEOUT" ]; then
        echo "[quiescence] ⚠ TIMEOUT ${TIMEOUT}s — 안정화 실패 (outbox o=$ob_order u=$ob_user, pending/paid=$pending, lag=$lag)"
        echo "[quiescence]   → 아직 변동 중: 비동기 미드레인(outbox/lag>0)이거나 PENDING 계속 변화. 부하/카오스 미종료 또는 재시도 폭주 의심."
        exit 2
    fi
    sleep "$INTERVAL"
done
