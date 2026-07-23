#!/usr/bin/env bash
# =============================================================================
# ADR-008 control(무방어) 이미지 빌드 — 무방어 전용 브랜치 `control/no-defense` 에서 빌드
#
# 운영 코드(feature = treatment)에는 방어를 끄는 스위치가 일절 없다(footgun 0). control(무방어) 소스는
# 별도 git 브랜치 `control/no-defense`(= 현재 feature − 6개 방어)로 관리하고, 이 스크립트는 그 브랜치 트리를
# git archive 로 임시 폴더에 풀어서 무방어 jar 를 빌드한 뒤 commerce-orderapi/userapi:control 로 태깅한다.
#
#   ★ 작업 소스를 전혀 건드리지 않는다 (오버레이의 cp/덮어쓰기/trap-복구 없음, 격리 빌드).
#   ★ drift: 한 번 측정이면 control/no-defense 를 feature 최신에서 뽑았으니 0. 반복 측정 시엔
#      `git checkout control/no-defense && git merge feature` 로 상류 변경을 반영(충돌=방어 제거 지점, 눈에 보임) 후 재빌드.
#   ★ ADR-008 §control 빌드: 변형 버그 오계수 방지용 무카오스 pre-flight 스모크 후 사용
#      (예: CHAOS=off ORDER_TARGET=2000 ../run-consistency.sh control C-smoke → verify N 이 0 근처인지).
#
# 사용: ./build-control-images.sh          (control/no-defense 브랜치가 있어야 함)
# 환경변수: NODEF_BRANCH(기본 control/no-defense)
# =============================================================================
set -euo pipefail
export MSYS_NO_PATHCONV=1
cd "$(dirname "$0")"            # qa/02-consistency/control
ROOT="../../.."                # repo root (git archive 는 어느 하위경로에서도 동작)
NODEF_BRANCH="${NODEF_BRANCH:-control/no-defense}"

if ! git rev-parse --verify --quiet "refs/heads/$NODEF_BRANCH" >/dev/null 2>&1; then
    echo "[control] ✗ 무방어 브랜치 '$NODEF_BRANCH' 가 없습니다."
    echo "         현재 feature 에서 6개 방어를 제거한 브랜치를 먼저 만드세요 (README §control 참고)."
    exit 1
fi

TMP="$(mktemp -d)/nodef"
mkdir -p "$TMP"
cleanup() { rm -rf "$(dirname "$TMP")"; }
trap cleanup EXIT INT TERM

echo "[control] '$NODEF_BRANCH' 트리를 임시 폴더로 추출 (git archive) ..."
git archive "$NODEF_BRANCH" | tar -x -C "$TMP"

echo "[control] 무방어 jar 빌드 (gradle bootJar -x test) ..."
chmod +x "$TMP/gradlew" 2>/dev/null || true
( cd "$TMP" && ./gradlew :orderApi:bootJar :userApi:bootJar -x test )

echo "[control] control 태그 이미지 빌드 ..."
docker build -t commerce-orderapi:control "$TMP/orderApi"
docker build -t commerce-userapi:control  "$TMP/userApi"

echo "[control] commerce-orderapi:control / commerce-userapi:control 빌드 완료 ✓ (작업 소스 무변경)"
