#!/usr/bin/env bash
# =============================================================================
# ADR-008 정합성 통합 시나리오 — 1회 실행 오케스트레이션 (브랜치 = arm)
#
#   run-consistency.sh <run_label>
#
# ★ 방어 상태는 "지금 체크아웃한 git 브랜치"가 결정한다 (control 오버레이/이미지 없음):
#     - 무방어 브랜치(control/no-defense, tag v1) 에서 실행 → control 측정 (라벨 예: C-run1)
#     - 방어 브랜치(feature/main) 에서 실행       → treatment 측정 (라벨 예: T-run1)
#   실행 전 반드시 그 브랜치 소스로 이미지를 빌드해 둘 것: ./build-images.sh
#
# 동작: down -v → up(orderapi ×N, --no-build) → ready 대기 → 시드(자립: user.sql→order.sql)
#       → k6(부하) + chaos(bounded T1–T4) 동시 → k6 종료 → 정착(quiescence) → verify(교차 DB 9체크) → down -v
#
# 환경변수: ORDER_TARGET(100000) ARRIVAL_RATE(200) N_ORDERAPI(4) CHAOS(on|off) RICH_POOL(300)
#           READY_WAIT(60) CHAOS_GRACE(60) KEEP(0: 종료 후 down -v)
# =============================================================================
set -uo pipefail
export MSYS_NO_PATHCONV=1
cd "$(dirname "$0")"

LABEL="${1:?run_label required (예: C-run1 / T-run1)}"

ORDER_TARGET="${ORDER_TARGET:-100000}"
ARRIVAL_RATE="${ARRIVAL_RATE:-200}"
N_ORDERAPI="${N_ORDERAPI:-4}"
CHAOS="${CHAOS:-on}"
RICH_POOL="${RICH_POOL:-300}"
READY_WAIT="${READY_WAIT:-60}"
KEEP="${KEEP:-0}"

COMPOSE_ARGS="-f docker-compose.qa.yml"
export COMPOSE_ARGS ORDER_TARGET ARRIVAL_RATE RICH_POOL
export RUN_LABEL="$LABEL"

BRANCH="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo '?')"
mkdir -p results
echo "================================================================="
echo " LABEL=$LABEL | branch=$BRANCH | target=$ORDER_TARGET rate=$ARRIVAL_RATE/s orderapi=$N_ORDERAPI chaos=$CHAOS"
echo "================================================================="

# 1) 정리 + 기동 (db-seed/k6 제외, --no-build: 이미지는 build-images.sh 로 사전 빌드)
docker compose $COMPOSE_ARGS down -v --remove-orphans 2>&1 | tail -2
echo "[$LABEL] 스택 기동 (orderapi ×$N_ORDERAPI) ..."
docker compose $COMPOSE_ARGS up -d --no-build --scale orderapi="$N_ORDERAPI" \
    mysql-user mysql-order redis kafka eureka userapi orderapi gateway

echo "[$LABEL] ready 대기 ${READY_WAIT}s (JVM 부팅 + Eureka 등록 + Gateway registry fetch) ..."
sleep "$READY_WAIT"

# 2) 시드: 자립 시나리오 (seller1 + ctrich + hot/normal SKU)
echo "[$LABEL] 시드 (자립: user.sql → order.sql) ..."
docker compose $COMPOSE_ARGS exec -T mysql-user  mysql -uroot -proot user   < ./seed/user.sql
docker compose $COMPOSE_ARGS exec -T mysql-order mysql -uroot -proot orders < ./seed/order.sql

# 3) 부하(k6) 백그라운드 + 카오스 동시
echo "[$LABEL] k6 부하 시작 (백그라운드) ..."
( RUN_LABEL="$LABEL" docker compose $COMPOSE_ARGS run --rm --no-deps \
    -e RUN_LABEL="$LABEL" -e ORDER_TARGET="$ORDER_TARGET" -e ARRIVAL_RATE="$ARRIVAL_RATE" \
    -e RICH_POOL="$RICH_POOL" \
    k6 run /scripts/load-test-consistency.js ) &
K6_PID=$!

CHAOS_PID=""
if [ "$CHAOS" = "on" ]; then
    echo "[$LABEL] 카오스 스케줄 시작 (bounded T1–T4) ..."
    ( COMPOSE_ARGS="$COMPOSE_ARGS" ORDER_TARGET="$ORDER_TARGET" bash chaos-schedule.sh ) &
    CHAOS_PID=$!
else
    echo "[$LABEL] CHAOS=off — 무카오스 baseline/pre-flight (무방어 브랜치 변형버그 스모크에도 사용)"
fi

wait "$K6_PID" || true
echo "[$LABEL] k6 종료"
# 부하 종료 후 카오스가 부하보다 오래 살지 않도록 grace 뒤 종료 (진행도 정체로 A80 앵커 미도달이어도 안전)
if [ -n "$CHAOS_PID" ]; then
    ( sleep "${CHAOS_GRACE:-60}"; kill "$CHAOS_PID" 2>/dev/null ) & GRACE_KILLER=$!
    wait "$CHAOS_PID" 2>/dev/null || true
    kill "$GRACE_KILLER" 2>/dev/null || true
fi

# 4) 정착(quiescence) 게이트
echo "[$LABEL] 정착 대기 ..."
COMPOSE_ARGS="$COMPOSE_ARGS" bash quiescence-gate.sh || true

# 5) 검증(교차 DB 9체크)
echo "[$LABEL] 검증 ..."
COMPOSE_ARGS="$COMPOSE_ARGS" RUN_LABEL="$LABEL" bash verify-consistency.sh || true

# 6) 정리
if [ "$KEEP" = "0" ]; then
    docker compose $COMPOSE_ARGS down -v --remove-orphans 2>&1 | tail -2
else
    echo "[$LABEL] KEEP=1 — 스택 유지 (수동 조사용). 정리: docker compose $COMPOSE_ARGS down -v"
fi
echo "[$LABEL] 완료 ✓ (branch=$BRANCH) → results/${LABEL}-verify.txt , results/${LABEL}-k6-summary.json"
