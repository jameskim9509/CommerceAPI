#!/usr/bin/env bash
# ADR-005 QA: k6 부하 진행 중에 docker stats + MySQL 상태를 5 초 간격으로 캡쳐.
#
# 사용: monitor-stats.sh <label>
# 종료: rm /tmp/qa-monitor.lock

set -uo pipefail

LABEL="${1:-unknown}"
LOCK=/tmp/qa-monitor.lock
INTERVAL=5

cd "$(dirname "$0")"   # 시나리오 폴더 (qa/01-orderapi-load-balancing)
RESULTS_DIR="results"
# 컨테이너명 qa-orderapi-load-balancing-* 는 docker-compose.qa.yml 의 `name: qa-orderapi-load-balancing` 으로 고정됨 (아래 grep/exec 가 이에 의존).

STATS_FILE="${RESULTS_DIR}/${LABEL}-stats.csv"
MYSQL_FILE="${RESULTS_DIR}/${LABEL}-mysql.csv"
EUREKA_FILE="${RESULTS_DIR}/${LABEL}-eureka.csv"

# 헤더
echo "timestamp,container,cpu_pct,mem_usage,mem_pct,net_io,block_io" > "$STATS_FILE"
echo "timestamp,threads_connected,threads_running,questions,slow_queries" > "$MYSQL_FILE"
echo "timestamp,app,instance_count,instance_ids" > "$EUREKA_FILE"

touch "$LOCK"
echo "[monitor] started for $LABEL, lock=$LOCK, interval=${INTERVAL}s"

while [ -f "$LOCK" ]; do
    TS=$(date +%H:%M:%S)

    # 1) docker stats (이 스택의 qa-orderapi-load-balancing-* 컨테이너만)
    #    docker ps 는 프로젝트 비한정 전역 목록이라 프로젝트명 접두사로 이 스택만 필터한다.
    #    (접두사가 시나리오 고유라 형제 시나리오 컨테이너와 겹치지 않는다.)
    docker stats --no-stream \
        --format '{{.Name}},{{.CPUPerc}},{{.MemUsage}},{{.MemPerc}},{{.NetIO}},{{.BlockIO}}' \
        $(docker ps --format '{{.Names}}' | grep '^qa-orderapi-load-balancing-' || true) 2>/dev/null \
        | sed "s|^|${TS},|; s|/|on|g; s| GiB||g; s| MiB||g; s|%||g" \
        >> "$STATS_FILE" || true

    # 2) MySQL 상태 (mysql-order)
    METRICS=$(docker exec qa-orderapi-load-balancing-mysql-order-1 mysql -uroot -proot -BN -e "
        SELECT
            (SELECT VARIABLE_VALUE FROM performance_schema.global_status WHERE VARIABLE_NAME='Threads_connected'),
            (SELECT VARIABLE_VALUE FROM performance_schema.global_status WHERE VARIABLE_NAME='Threads_running'),
            (SELECT VARIABLE_VALUE FROM performance_schema.global_status WHERE VARIABLE_NAME='Questions'),
            (SELECT VARIABLE_VALUE FROM performance_schema.global_status WHERE VARIABLE_NAME='Slow_queries');
    " 2>/dev/null | tr -d '\n' || echo ",,,")

    echo "${TS},${METRICS}" >> "$MYSQL_FILE"

    # 3) Eureka registry: 각 app 의 인스턴스 수 + instanceId 목록
    # Accept: application/json 로 받아 jq 처럼 parse (없으면 grep 으로 대체)
    EUREKA_JSON=$(curl -sfS -H "Accept: application/json" http://localhost:8761/eureka/apps 2>/dev/null || echo '{}')
    for APP in ORDER-API USER-API GATEWAY; do
        # 정규식으로 instanceId 추출 (간단한 multi-line grep)
        IDS=$(echo "$EUREKA_JSON" | tr ',' '\n' | grep -A1 "\"name\":\"$APP\"" \
              | grep -oE '"instanceId":"[^"]+"' | sed 's/"instanceId":"//;s/"$//' | tr '\n' '|' || echo '')
        CNT=$(echo "$IDS" | tr '|' '\n' | grep -c '.' || echo 0)
        echo "${TS},${APP},${CNT},${IDS}" >> "$EUREKA_FILE"
    done

    sleep "$INTERVAL"
done

echo "[monitor] stopped for $LABEL"
