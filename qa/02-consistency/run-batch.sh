#!/usr/bin/env bash
# =============================================================================
# ADR-008 정합성 측정 — 런 배치 드라이버 (README §실행 3 의 수동 절차를 그대로 자동화)
#
# 사용: bash run-batch.sh <PREFIX> <START> <END>
#   예) bash run-batch.sh C 1 10     → C-run1 … C-run10
#       bash run-batch.sh C smoke    → C-smoke (무카오스 소부하 스모크)
#
# 절차(README §실행 3 과 1:1):
#   down -v → up -d --wait → db-seed → sleep 60 → chaos(백그라운드) → k6 → chaos kill
#   → quiescence-gate → verify → down -v
#
# ★ ARRIVAL_RATE 는 k6 와 chaos-schedule.sh 에 반드시 같은 값이 가야 한다
#   (chaos 가 진행도 앵커를 t = N ÷ rate 로 환산한다). 여기서 한 번만 export 한다.
# ★ 실패한 런은 results/<LABEL>-FAILED 마커를 남기고 다음 런으로 넘어간다 —
#   배치 전체가 멈추지 않게 하되 집계에서 배제할 수 있어야 하므로.
# =============================================================================
set -uo pipefail
export MSYS_NO_PATHCONV=1
cd "$(dirname "$0")"

COMPOSE="docker compose -f docker-compose.qa.yml"
PREFIX="${1:?라벨 접두 필요 (C 또는 T)}"
MODE="${2:?시작 번호 또는 'smoke'}"
END="${3:-$MODE}"

export ORDERAPI_REPLICAS="${ORDERAPI_REPLICAS:-4}"
export USERAPI_REPLICAS="${USERAPI_REPLICAS:-2}"
export DB_CONN_TIMEOUT_MS="${DB_CONN_TIMEOUT_MS:-1000}"
export ARRIVAL_RATE="${ARRIVAL_RATE:-90}"
export RICH_POOL="${RICH_POOL:-300}"

log() { echo "[batch $(date -u +%H:%M:%S)] $*"; }

run_one() {
    local LABEL="$1" TARGET="$2" CHAOS="$3"
    local t0=$(date +%s)
    log "=== $LABEL 시작 (target=$TARGET rate=$ARRIVAL_RATE chaos=$CHAOS) ==="

    $COMPOSE down -v --remove-orphans >/dev/null 2>&1

    log "$LABEL 스택 기동"
    if ! $COMPOSE up -d --wait mysql-user mysql-order redis kafka eureka userapi orderapi gateway; then
        log "⚠ $LABEL 기동 실패 — 폐기"; echo "기동 실패 (up -d --wait)" > "results/${LABEL}-FAILED"; $COMPOSE down -v >/dev/null 2>&1; return 1
    fi
    $COMPOSE up -d db-seed >/dev/null 2>&1
    $COMPOSE wait db-seed >/dev/null 2>&1
    log "$LABEL 시드 완료 — 레지스트리 전파 60s 대기"
    sleep 60

    local CHAOS_PID=""
    if [ "$CHAOS" = "yes" ]; then
        ORDER_TARGET="$TARGET" bash chaos-schedule.sh > "results/${LABEL}-chaos.log" 2>&1 &
        CHAOS_PID=$!
        log "$LABEL 카오스 스케줄 시작 (pid=$CHAOS_PID)"
    fi

    # ★ --summary-export 를 쓰면 안 된다 — 스크립트의 handleSummary() 가 직접
    #   /results/<LABEL>-k6-summary.json 에 커스텀 요약(params·replay 카운터 등)을 쓰는데,
    #   --summary-export 가 같은 경로를 k6 레거시 포맷으로 덮어써 verify 의 '의도' 파싱이 깨진다.
    log "$LABEL k6 부하 시작"
    RUN_LABEL="$LABEL" ORDER_TARGET="$TARGET" ARRIVAL_RATE="$ARRIVAL_RATE" \
        $COMPOSE run --rm --no-deps k6 run \
        /scripts/load-test-consistency.js > "results/${LABEL}-k6.log" 2>&1
    log "$LABEL k6 종료"

    if [ -n "$CHAOS_PID" ]; then
        kill "$CHAOS_PID" 2>/dev/null
        pkill -P "$CHAOS_PID" 2>/dev/null
        wait "$CHAOS_PID" 2>/dev/null
        # ★ 스케줄이 끝까지 갔는지 확인한다 — 주입 중 docker 명령이 걸리면(T1 kill 후
        #   재기동 hang 등) 스케줄이 그 자리에 멈추고 남은 주입이 통째로 빠진다.
        #   그 런은 '주입 조합이 다른 실험'인데 N 은 그럴듯한 값으로 나와 로그 없이는
        #   구분되지 않는다. 마커를 남겨 aggregate 가 빼도록 한다.
        if ! grep -q "카오스 스케줄 종료" "results/${LABEL}-chaos.log" 2>/dev/null; then
            log "⚠ $LABEL 카오스 스케줄 미완주 — FAILED 마커 기록(집계 제외)"
            echo "카오스 스케줄 미완주 — 주입 조합이 다름 (chaos.log 참조)" > "results/${LABEL}-FAILED"
        fi
    fi

    log "$LABEL 정착 대기"
    RUN_LABEL="$LABEL" bash quiescence-gate.sh >/dev/null 2>&1
    local qexit=$?
    [ "$qexit" -ne 0 ] && log "⚠ $LABEL 정착 게이트 exit=$qexit (TIMEOUT 가능) — verify 는 진행하되 산출물에 기록됨"

    log "$LABEL 검증"
    RUN_LABEL="$LABEL" bash verify-consistency.sh >/dev/null 2>&1

    $COMPOSE down -v >/dev/null 2>&1
    local dt=$(( $(date +%s) - t0 ))
    log "=== $LABEL 완료 (${dt}s) — $(grep -oE '총 위반 건수.* N = [0-9]+' "results/${LABEL}-verify.txt" 2>/dev/null || echo 'N 파싱 실패') ==="
}

if [ "$MODE" = "smoke" ]; then
    run_one "${PREFIX}-smoke" 2000 no
else
    for i in $(seq "$MODE" "$END"); do
        run_one "${PREFIX}-run${i}" 100000 yes
    done
fi

log "배치 종료"
