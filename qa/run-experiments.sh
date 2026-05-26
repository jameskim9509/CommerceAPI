#!/usr/bin/env bash
# =============================================================================
# ADR-005 시나리오 3: E1 / E2 / E3 자동 측정 오케스트레이션
#
# 사전:
#   - Docker daemon 실행 중
#   - qa/docker-compose.qa.yml, qa/k6/load-test.js, qa/seed/*.sql 존재
#
# 동작:
#   for N in 1 2 4:
#     1. 스택 down
#     2. up --scale orderapi=N
#     3. 모든 서비스 ready 대기
#     4. 시드 SQL 실행
#     5. k6 부하 테스트 실행 (5 분)
#     6. 결과 / 잔여 PENDING / 멱등성 위반 수집
#
# 사용:  ./qa/run-experiments.sh
# =============================================================================

set -euo pipefail

cd "$(dirname "$0")/.."   # 프로젝트 루트

QA_DIR="qa"
COMPOSE_FILE="$QA_DIR/docker-compose.qa.yml"
RESULTS_DIR="$QA_DIR/results"

mkdir -p "$RESULTS_DIR"

experiments=(1 2 4)
experiment_labels=(E1 E2 E3)

for i in "${!experiments[@]}"; do
    N="${experiments[$i]}"
    LABEL="${experiment_labels[$i]}"
    echo ""
    echo "================================================================="
    echo " 실험 $LABEL : orderApi 인스턴스 $N 개"
    echo "================================================================="

    # 1. 완전 정리 (이전 실험의 잔재 제거)
    docker compose -f "$COMPOSE_FILE" down -v --remove-orphans 2>&1 | tail -3

    # 2. 스택 기동
    echo "[$LABEL] 스택 기동 (--scale orderapi=$N) ..."
    EXPERIMENT_LABEL="$LABEL" docker compose -f "$COMPOSE_FILE" up -d \
        --scale orderapi="$N" \
        mysql-user mysql-order redis kafka eureka userapi orderapi gateway

    # 3. ready 대기 (gateway 가 백엔드 라우팅 준비될 때까지)
    echo "[$LABEL] 서비스 ready 대기 ..."
    sleep 60   # JVM 부팅 + Eureka 등록 + Gateway registry fetch

    # 4. 시드 SQL 실행
    echo "[$LABEL] 시드 SQL 실행 ..."
    docker compose -f "$COMPOSE_FILE" exec -T mysql-user \
        mysql -uroot -proot user < "$QA_DIR/seed/user-seed.sql"
    docker compose -f "$COMPOSE_FILE" exec -T mysql-order \
        mysql -uroot -proot orders < "$QA_DIR/seed/order-seed.sql"

    # 5. k6 부하 테스트
    echo "[$LABEL] k6 부하 테스트 시작 ..."
    TEST_START=$(date -u +%FT%TZ)
    EXPERIMENT_LABEL="$LABEL" docker compose -f "$COMPOSE_FILE" run --rm k6 \
        run --summary-export "/results/${LABEL}-k6-summary.json" \
        /scripts/load-test.js
    TEST_END=$(date -u +%FT%TZ)

    # 6. 부하 종료 후 안정화 대기 (Outbox 처리 + SAGA 완결)
    echo "[$LABEL] Outbox / SAGA 정착 대기 (60s) ..."
    sleep 60

    # 7. PENDING / 멱등성 위반 / SAGA latency 수집
    echo "[$LABEL] 결과 수집 ..."

    {
        echo "## $LABEL — orderApi 인스턴스 $N 개"
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
        echo ""
        echo "### SAGA end-to-end p99 (created → updated)"
        docker compose -f "$COMPOSE_FILE" exec -T mysql-order mysql -uroot -proot -e "
            SELECT
                COUNT(*) AS n,
                AVG(TIMESTAMPDIFF(MICROSECOND, created_date, modified_date)) / 1000 AS avg_ms,
                MAX(TIMESTAMPDIFF(MICROSECOND, created_date, modified_date)) / 1000 AS max_ms
            FROM orders.orders
            WHERE created_date >= '$TEST_START' AND status IN ('CONFIRMED','FAILED');
        " 2>&1 || true
    } > "$RESULTS_DIR/${LABEL}-server-side.md"

    # 8. orderApi 컨테이너 자원 사용량 스냅샷
    docker compose -f "$COMPOSE_FILE" ps --format '{{.Name}}' | grep orderapi | \
        xargs -I{} docker stats --no-stream --format \
        "{{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}" {} > "$RESULTS_DIR/${LABEL}-docker-stats.txt" 2>&1 || true

    echo "[$LABEL] 완료 ✓"
done

echo ""
echo "================================================================="
echo " 전체 실험 종료. 결과: $RESULTS_DIR/"
echo "================================================================="
ls -la "$RESULTS_DIR/"
