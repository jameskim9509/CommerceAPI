#!/usr/bin/env bash
# =============================================================================
# ADR-008 §재현 하네스 3 — 정착(quiescence) 게이트
#
# 고정 sleep 이 아니라, 비동기 SAGA 흐름(보상 이벤트·아웃박스 발행)이 실제로 멎었는지 폴링으로 확인한다.
# 다음 4개가 K회 연속 만족하면 "정착"으로 판정:
#   1) orders DB outbox 미발행 = 0        (sent_at IS NULL)
#   2) user   DB outbox 미발행 = 0
#   3) orders DB 중간 상태(PENDING/PAID) 스코프 잔여 = 0
#   4) kafka consumer lag 합 = 0           (best-effort — kafka 재생성(T4) 직후 group 소실은 lag 0 취급)
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
while true; do
    ob_order=$(num "$(q_order 'SELECT COUNT(*) FROM outbox_events WHERE sent_at IS NULL;')")
    ob_user=$(num "$(q_user  'SELECT COUNT(*) FROM outbox_events WHERE sent_at IS NULL;')")
    pending=$(num "$(q_order "SELECT COUNT(*) FROM orders WHERE status IN ('PENDING','PAID') AND ($SCOPE);")")
    lag=$(num "$(kafka_lag)")

    if [ "$ob_order" = "0" ] && [ "$ob_user" = "0" ] && [ "$pending" = "0" ] && [ "$lag" = "0" ]; then
        stable=$((stable + 1))
        echo "[quiescence] quiet round $stable/$STABLE_ROUNDS (outbox o=$ob_order u=$ob_user, pending/paid=$pending, lag=$lag)"
        [ "$stable" -ge "$STABLE_ROUNDS" ] && { echo "[quiescence] 정착 완료 ✓"; exit 0; }
    else
        [ "$stable" -ne 0 ] && echo "[quiescence] reset (outbox o=$ob_order u=$ob_user, pending/paid=$pending, lag=$lag)"
        stable=0
    fi

    now=$(date +%s)
    if [ $((now - start)) -ge "$TIMEOUT" ]; then
        echo "[quiescence] ⚠ TIMEOUT ${TIMEOUT}s — 정착 미완료 (outbox o=$ob_order u=$ob_user, pending/paid=$pending, lag=$lag)"
        echo "[quiescence]   → 주변 장애(T2 이벤트 드롭 / T4 로그 전소)로 SAGA 가 영구 정지했을 수 있다 (verify 가 잔여 r 로 계수)."
        exit 2
    fi
    sleep "$INTERVAL"
done
