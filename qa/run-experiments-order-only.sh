#!/usr/bin/env bash
# =============================================================================
# ADR-005 시나리오 3 — order-only 버전 자동 실행
#
# load-test-order-only.js 를 사용해 카트를 setup 에서 미리 시딩하고
# default 함수에서 order endpoint 만 부하한다.
#
# 결과 파일은 E{1,2,3}o 접두사 (o = order-only).
# =============================================================================
export MSYS_NO_PATHCONV=1

set -euo pipefail
cd "$(dirname "$0")/.."

QA_DIR="qa"
COMPOSE_FILE="$QA_DIR/docker-compose.qa.yml"
RESULTS_DIR="$QA_DIR/results"
mkdir -p "$RESULTS_DIR"

experiments=(1 2 4)
labels=(E1o E2o E3o)

for i in "${!experiments[@]}"; do
    N="${experiments[$i]}"
    LABEL="${labels[$i]}"
    echo ""
    echo "================================================================="
    echo " 실험 $LABEL : orderApi 인스턴스 $N 개 (order-only)"
    echo "================================================================="

    docker compose -f "$COMPOSE_FILE" down -v --remove-orphans 2>&1 | tail -3

    echo "[$LABEL] 스택 기동 (--scale orderapi=$N) ..."
    EXPERIMENT_LABEL="$LABEL" docker compose -f "$COMPOSE_FILE" up -d \
        --scale orderapi="$N" \
        mysql-user mysql-order redis kafka eureka userapi orderapi gateway

    echo "[$LABEL] 서비스 ready 대기 ..."
    sleep 60

    echo "[$LABEL] DB 시드 ..."
    docker compose -f "$COMPOSE_FILE" exec -T mysql-user mysql -uroot -proot user < "$QA_DIR/seed/user-seed.sql"
    docker compose -f "$COMPOSE_FILE" exec -T mysql-order mysql -uroot -proot orders < "$QA_DIR/seed/order-seed.sql"

    echo "[$LABEL] 모니터링 시작 ..."
    bash "$QA_DIR/monitor-stats.sh" "$LABEL" &
    MONITOR_PID=$!

    echo "[$LABEL] k6 (order-only) 실행 ..."
    TEST_START=$(date -u +%FT%TZ)
    EXPERIMENT_LABEL="$LABEL" docker compose -f "$COMPOSE_FILE" run --rm --no-deps k6 \
        run --summary-export "/results/${LABEL}-k6-summary.json" \
        /scripts/load-test-order-only.js || true
    TEST_END=$(date -u +%FT%TZ)

    rm -f /tmp/qa-monitor.lock
    wait "$MONITOR_PID" 2>/dev/null || true

    echo "[$LABEL] SAGA 정착 대기 60s ..."
    sleep 60

    echo "[$LABEL] 결과 수집 ..."
    {
        echo "## $LABEL — orderApi 인스턴스 $N 개 (order-only)"
        echo ""
        echo "- 측정 시작: $TEST_START"
        echo "- 측정 종료: $TEST_END"
        echo ""
        echo "### 주문 상태 분포"
        docker compose -f "$COMPOSE_FILE" exec -T mysql-order mysql -uroot -proot -e "
            SELECT status, COUNT(*) AS cnt
            FROM orders.orders
            WHERE created_date >= '$TEST_START'
            GROUP BY status;
        " 2>&1 || true
        echo ""
        echo "### Outbox 미발행 잔여"
        docker compose -f "$COMPOSE_FILE" exec -T mysql-order mysql -uroot -proot -e "
            SELECT COUNT(*) AS unsent FROM orders.outbox_events WHERE sent_at IS NULL;
        " 2>&1 || true
    } > "$RESULTS_DIR/${LABEL}-server-side.md"

    echo "[$LABEL] 완료 ✓"
done

echo ""
echo "================================================================="
echo " order-only 전체 실험 종료. 결과: $RESULTS_DIR/E*o-*"
echo "================================================================="
