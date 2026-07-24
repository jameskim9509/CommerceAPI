#!/usr/bin/env bash
# k6 부하 진행 중에 docker stats + MySQL 상태를 5 초 간격으로 캡쳐.
#
# 사용: monitor-stats.sh <label>
# 종료: rm /tmp/qa-monitor.lock

set -uo pipefail

LABEL="${1:-unknown}"
LOCK=/tmp/qa-monitor.lock
INTERVAL=5

cd "$(dirname "$0")"   # 시나리오 폴더 (qa/01-orderapi-load-balancing)
RESULTS_DIR="results"

STATS_FILE="${RESULTS_DIR}/${LABEL}-stats.csv"
MYSQL_FILE="${RESULTS_DIR}/${LABEL}-mysql.csv"

# 헤더
echo "timestamp,container,cpu_pct" > "$STATS_FILE"
echo "timestamp,threads_connected,threads_running,questions" > "$MYSQL_FILE"

touch "$LOCK"
echo "[monitor] started for $LABEL, lock=$LOCK, interval=${INTERVAL}s"

while [ -f "$LOCK" ]; do
    TS=$(date +%H:%M:%S)

    # 1) docker stats (qa-orderapi-load-balancing-* 컨테이너만)
    docker stats --no-stream \
        --format '{{.Name}},{{.CPUPerc}}' \
        $(docker ps --format '{{.Names}}' | grep '^qa-orderapi-load-balancing-' || true) 2>/dev/null \
        | sed "s|^|${TS},|; s|%||g" \
        >> "$STATS_FILE" || true

    # 2) MySQL 상태 (mysql-order)
    METRICS=$(docker exec qa-orderapi-load-balancing-mysql-order-1 mysql -uroot -proot -BN -e "
        SELECT
            (SELECT VARIABLE_VALUE FROM performance_schema.global_status WHERE VARIABLE_NAME='Threads_connected'),
            (SELECT VARIABLE_VALUE FROM performance_schema.global_status WHERE VARIABLE_NAME='Threads_running'),
            (SELECT VARIABLE_VALUE FROM performance_schema.global_status WHERE VARIABLE_NAME='Questions');
    " 2>/dev/null | tr -d '\n' || echo ",,,")

    echo "${TS},${METRICS}" >> "$MYSQL_FILE"

    sleep "$INTERVAL"
done

echo "[monitor] stopped for $LABEL"
