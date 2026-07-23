#!/usr/bin/env bash
# =============================================================================
# ADR-008 control(무방어) 이미지 빌드
#
# 6종 방어 제거 방식(하이브리드):
#   ①③④⑥ : 운영 소스에 이미 있는 @Value/@ConditionalOnProperty 플래그를 docker-compose.control.yml 이 env 로 OFF.
#   ②⑤    : JPA @Version 은 프로퍼티로 못 끄므로, 이 스크립트가 빌드 중에만 @Version 제거 오버레이를 덮어써
#            무방어 jar 를 만들고 commerce-orderapi:control / commerce-userapi:control 로 태깅한다.
#
# ★ 오버레이는 "빌드 중에만" 적용되고 trap 으로 항상 원본을 복구한다 (운영 소스 오염 없음).
# ★ ADR-008 §control 빌드: 변형 버그가 위반으로 오계수되지 않게, 무카오스 clean 부하로 pre-flight 스모크 후 사용.
#      예: CHAOS=off ORDER_TARGET=2000 ../run-consistency.sh control C-smoke   → verify N 이 0 근처인지 확인.
#
# 사용: ./build-control-images.sh
# =============================================================================
set -euo pipefail
export MSYS_NO_PATHCONV=1
cd "$(dirname "$0")"            # qa/02-consistency/control
ROOT="../../.."                # repo root

PI="$ROOT/orderApi/src/main/java/com/zerobase/orderApi/domain/ProductItem.java"
CU="$ROOT/userApi/src/main/java/com/zerobase/userApi/domain/customer/Customer.java"

restore() {
    [ -f "$PI.orig" ] && mv -f "$PI.orig" "$PI"
    [ -f "$CU.orig" ] && mv -f "$CU.orig" "$CU"
    echo "[control] 원본 @Version 소스 복구됨"
}
trap restore EXIT INT TERM

echo "[control] @Version 제거 오버레이 적용 (원본 백업 .orig) ..."
cp "$PI" "$PI.orig"; cp "$CU" "$CU.orig"
cp overlay/ProductItem.java "$PI"
cp overlay/Customer.java    "$CU"

echo "[control] 무방어 jar 빌드 (gradle bootJar -x test) ..."
( cd "$ROOT" && ./gradlew :orderApi:bootJar :userApi:bootJar -x test )

echo "[control] control 태그 이미지 빌드 ..."
docker build -t commerce-orderapi:control "$ROOT/orderApi"
docker build -t commerce-userapi:control  "$ROOT/userApi"

echo "[control] commerce-orderapi:control / commerce-userapi:control 빌드 완료 ✓"
# trap restore 가 EXIT 에서 원본을 복구 → 이후 treatment 빌드는 정상 @Version 소스로 진행된다.
