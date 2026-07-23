#!/usr/bin/env bash
# =============================================================================
# ADR-008 control(무방어) 이미지 빌드 — 6종 방어를 전부 소스 오버레이로 제거
#
# 운영 코드에는 방어를 끄는 스위치(토글/프로퍼티)가 일절 없다(footgun 방지). 대신 이 스크립트가
# overlay/ 아래 "무방어본" 소스를 빌드 중에만 원본 위에 덮어써 무방어 jar 를 만들고
# commerce-orderapi:control / commerce-userapi:control 로 태깅한 뒤 trap 으로 원본을 복구한다.
#
# overlay/ 는 소스 트리를 그대로 미러링한다 (overlay/<module>/src/main/java/... = 원본 상대경로):
#   ① IdempotencyService        (멱등 게이트 제거)
#   ② ProductItem @Version       (재고 낙관적 락 제거)
#   ③ RefundConsumer            (환불 보상 제거)
#   ④ CustomerBalanceHistoryService (잔액 검증 제거)
#   ⑤ Customer @Version          (잔액 낙관적 락 제거)
#   ⑥ IdempotentEventHandler ×2  (processed_events dedup 제거; userApi 는 낙관적락 재시도도 제거)
#
# ★ ADR-008 §control 빌드: 변형 버그가 위반으로 오계수되지 않게, 무카오스 clean 부하로 pre-flight 스모크 후 사용.
#      예: CHAOS=off ORDER_TARGET=2000 ../run-consistency.sh control C-smoke  → verify N 이 0 근처인지 확인.
#
# 사용: ./build-control-images.sh
# =============================================================================
set -euo pipefail
export MSYS_NO_PATHCONV=1
cd "$(dirname "$0")"            # qa/02-consistency/control
ROOT="../../.."                # repo root
OVERLAY="overlay"

# 적용된 원본 백업(.orig)을 전부 되돌린다 (gradle 실패·중단에도 원본 보존).
restore() {
    find "$ROOT/orderApi/src" "$ROOT/userApi/src" -name '*.orig' 2>/dev/null | while IFS= read -r o; do
        mv -f "$o" "${o%.orig}"
    done
    echo "[control] 원본 소스 복구됨"
}
trap restore EXIT INT TERM
restore   # 이전 실행이 남긴 stale .orig 정리 (있다면)

echo "[control] 무방어 오버레이 적용 (${OVERLAY}/ → 원본, 백업 .orig) ..."
while IFS= read -r ov; do
    dest="$ROOT/${ov#"$OVERLAY"/}"
    if [ ! -f "$dest" ]; then
        echo "[control] ✗ 오버레이 대상 원본이 없습니다: $dest (소스 이동/리네임됨?)"
        exit 1
    fi
    cp "$dest" "$dest.orig"
    cp "$ov" "$dest"
    echo "  overlay → ${dest#"$ROOT"/}"
done < <(find "$OVERLAY" -type f -name '*.java')

echo "[control] 무방어 jar 빌드 (gradle bootJar -x test) ..."
( cd "$ROOT" && ./gradlew :orderApi:bootJar :userApi:bootJar -x test )

echo "[control] control 태그 이미지 빌드 ..."
docker build -t commerce-orderapi:control "$ROOT/orderApi"
docker build -t commerce-userapi:control  "$ROOT/userApi"

echo "[control] commerce-orderapi:control / commerce-userapi:control 빌드 완료 ✓"
# trap restore 가 EXIT 에서 원본을 복구 → 이후 treatment 빌드는 정상(방어 ON) 소스로 진행된다.
