#!/usr/bin/env bash
# =============================================================================
# ADR-008 — 현재 브랜치(=arm)에서 N회 반복 실행 + 집계 (점추정 금지)
#
#   run-repeat.sh <N> <label_prefix>
#
# 브랜치 = arm 모델이라, control 과 treatment 를 한 스크립트에서 번갈아 돌리지 않는다. 대신:
#   git checkout control/no-defense (tag v1) → ./run-repeat.sh 10 C   → C-run1..N (무방어 N 분포)
#   git checkout feature/main                → ./run-repeat.sh 10 T   → T-run1..N (방어 r 분포)
#   → 두 AGGREGATE 의 중앙값을 비교: N(control) → r(treatment)
#
# ADR-008 §재현 하네스 4: 전 run 원값·중앙값·범위 공표(점추정 금지). 한 번만(N=1) 돌려도 되지만 리포트에 명시.
# =============================================================================
set -uo pipefail
export MSYS_NO_PATHCONV=1
cd "$(dirname "$0")"

N="${1:?N 회수 필요 (예: 10, 또는 1)}"
PREFIX="${2:?라벨 접두 필요 (예: C 또는 T)}"
mkdir -p results
AGG="results/${PREFIX}-AGGREGATE.md"

BRANCH="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo '?')"
echo "[repeat] branch=$BRANCH prefix=$PREFIX N=$N — 이미지 빌드(현재 브랜치 소스) ..."
./build-images.sh

for i in $(seq 1 "$N"); do
    echo "===== $PREFIX run $i/$N (branch=$BRANCH) ====="
    bash run-consistency.sh "${PREFIX}-run$i" || true
done

# --- 집계 (verify.txt 에서 N·money_leak 파싱) ---
extract_N()    { grep -oE '집계 KPI.* N = [0-9]+' "$1" 2>/dev/null | grep -oE '[0-9]+$' | head -1; }
extract_leak() { grep -oE '돈 보존 누수\(원\) = -?[0-9]+' "$1" 2>/dev/null | grep -oE '\-?[0-9]+$' | head -1; }
median() { printf '%s\n' "$@" | sort -n | awk '{a[NR]=$0} END{if(NR==0){print "n/a"} else if(NR%2){print a[(NR+1)/2]} else {print (a[NR/2]+a[NR/2+1])/2}}'; }
minv()   { printf '%s\n' "$@" | sort -n | head -1; }
maxv()   { printf '%s\n' "$@" | sort -n | tail -1; }

vals=()
{
    echo "# ADR-008 정합성 측정 집계 — arm=$PREFIX (branch=$BRANCH)"
    echo ""
    echo "- 파라미터: ORDER_TARGET=${ORDER_TARGET:-100000}, ARRIVAL_RATE=${ARRIVAL_RATE:-200}/s, N_ORDERAPI=${N_ORDERAPI:-4}, CHAOS=${CHAOS:-on}, 반복=$N"
    echo ""
    echo "| run | 위반 총수(N/r) | money_leak(원) |"
    echo "|----:|---------------:|---------------:|"
    for i in $(seq 1 "$N"); do
        f="results/${PREFIX}-run$i-verify.txt"
        v=$(extract_N "$f");   v=${v:-"?"}
        l=$(extract_leak "$f"); l=${l:-"?"}
        echo "| $i | $v | $l |"
        [ "$v" != "?" ] && vals+=("$v")
    done
    echo ""
    if [ "${#vals[@]}" -gt 0 ]; then
        echo "**요약**: median=$(median "${vals[@]}")  range=[$(minv "${vals[@]}")..$(maxv "${vals[@]}")]  (n=${#vals[@]})"
        [ "$N" = "1" ] && echo ""
        [ "$N" = "1" ] && echo "> ⚠ **N=1 point estimate** — ADR-008 §재현 하네스 4 는 ≥10회 interleaved 권장."
    else
        echo "> ⟨측정전⟩ — 실행 결과 없음."
    fi
    echo ""
    echo "> KPI 는 이 arm 의 중앙값을 반대편 arm(다른 브랜치) 과 비교: N(control) → r(treatment)."
} > "$AGG"

echo "[repeat] 완료 → $AGG"
cat "$AGG"
