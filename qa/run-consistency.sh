#!/usr/bin/env bash
# =============================================================================
# 정합성 통합 시나리오 오케스트레이션 (단일 실행)
#   orderApi 4 인스턴스 + Kafka 4 파티션으로 띄우고,
#   scenario 시드 주입 → 통합 k6 부하 → 정착 대기 → 3대 불변식 검증.
#
# 사용:  bash qa/run-consistency.sh
# =============================================================================
export MSYS_NO_PATHCONV=1
set -uo pipefail
cd "$(dirname "$0")/.."

CF=(-f qa/docker-compose.qa.yml -f qa/docker-compose.consistency.yml)
RESULTS=qa/results
mkdir -p "$RESULTS"

echo "[1/6] 이전 스택 정리 (down -v) ..."
docker compose "${CF[@]}" down -v --remove-orphans 2>&1 | tail -2

echo "[2/6] 스택 기동 (--build, --scale orderapi=4, Kafka 4 파티션) ..."
# --build: 캐시된 stale 이미지(예: 구버전 userApi 의 MAILGUN_APIKEY) 대신 현재 소스로 재빌드
docker compose "${CF[@]}" up -d --build --scale orderapi=4 \
    mysql-user mysql-order redis kafka eureka userapi orderapi gateway

echo "[3/6] 서비스/스키마 ready 대기 ..."
sleep 60
# Flyway 가 테이블 만들 때까지 폴링
for i in $(seq 1 40); do
    if docker compose "${CF[@]}" exec -T mysql-order mysql -uroot -proot orders -e 'SELECT 1 FROM product LIMIT 1' >/dev/null 2>&1 \
    && docker compose "${CF[@]}" exec -T mysql-user  mysql -uroot -proot user   -e 'SELECT 1 FROM customer LIMIT 1' >/dev/null 2>&1; then
        echo "  schema ready (after $((60 + i*5))s 근사)"; break
    fi
    sleep 5
done

echo "[4/6] scenario 시드 주입 (user → order) ..."
docker compose "${CF[@]}" exec -T mysql-user  mysql -uroot -proot user   < qa/seed/scenario/user.sql
docker compose "${CF[@]}" exec -T mysql-order mysql -uroot -proot orders < qa/seed/scenario/order.sql

# ★ 로그인 readiness 게이트: gateway→userApi(Eureka LB) 가 실제로 200 을 줄 때까지 대기.
#   (userApi 미등록/크래시 시 503 → 부하가 공허하게 통과하는 사고 방지)
echo "[4/6] 로그인 readiness 게이트 (gateway→user-api 200 대기) ..."
ready=0
for i in $(seq 1 40); do
    code=$(curl -s -o /dev/null -w '%{http_code}' -X POST http://localhost:8080/user/customer/login \
        -H 'Content-Type: application/json' \
        -d '{"email":"ctrich1@qa.test","password":"password"}' 2>/dev/null || echo 000)
    # Windows curl 이 -o /dev/null 쓰기에서 비정상 종료코드를 내며 코드 뒤에 000 이 붙을 수 있어 prefix 매칭
    if [[ "$code" == 200* ]]; then echo "  login ready (HTTP 200, ${i}회차)"; ready=1; break; fi
    echo "  ...아직 준비 안됨 (HTTP $code), 5s 후 재시도 ($i/40)"
    sleep 5
done
if [ "$ready" != "1" ]; then
    echo "  ❌ 로그인 게이트 타임아웃 — userApi 미가용. 중단."
    docker compose "${CF[@]}" ps -a --format '{{.Name}}\t{{.Status}}'
    exit 1
fi

echo "[5/6] k6 통합 부하 (--no-deps) ..."
EXPERIMENT_LABEL=CONSIST docker compose "${CF[@]}" run --rm --no-deps k6 \
    run --summary-export "/results/CONSIST-k6-summary.json" \
    /scripts/load-test-consistency.js || true

echo "[5/6] Outbox/SAGA 정착 대기 (180s) ..."
sleep 180

echo "[6/6] 불변식 검증 ..."
bash qa/verify-consistency.sh | tee "$RESULTS/CONSIST-verify.txt"

echo ""
echo "완료. 산출물: $RESULTS/CONSIST-verify.txt, $RESULTS/CONSIST-summary.json"
