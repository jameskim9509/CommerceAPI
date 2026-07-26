#!/usr/bin/env bash
# =============================================================================
# ADR-008 — 한 arm(= 한 브랜치)의 run 결과 집계 (점추정 금지)
#
#   aggregate-runs.sh <label_prefix>      예: ./aggregate-runs.sh T
#
# 수동 측정 절차(README §실행)로 쌓인 results/<PREFIX>-run<i>-verify.txt 를 모아
# 위반 총수 N·돈 누수의 원값·중앙값·범위를 results/<PREFIX>-AGGREGATE.md 로 공표한다.
# 실행 자체는 하지 않는다 — 이미 끝난 run 들을 읽어 표로 만들 뿐.
#
# 브랜치 = arm 이라 control 과 treatment 는 각 브랜치에서 따로 돌리고 따로 집계한다:
#   control/no-defense (tag v1) 에서 C-run1..N → ./aggregate-runs.sh C   (무방어 N 분포)
#   방어 브랜치 (feature/main)  에서 T-run1..N → ./aggregate-runs.sh T   (방어   r 분포)
#   → 두 AGGREGATE 의 중앙값을 비교: N(control) → r(treatment)
#
# ADR-008 §재현 하네스 4: 전 run 원값·중앙값·범위 공표(점추정 금지). 한 번만(N=1) 돌려도 되지만 리포트에 명시.
# =============================================================================
set -uo pipefail
export MSYS_NO_PATHCONV=1
cd "$(dirname "$0")"

PREFIX="${1:?라벨 접두 필요 (예: C 또는 T)}"
mkdir -p results
AGG="results/${PREFIX}-AGGREGATE.md"
BRANCH="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo '?')"

# --- 파싱 (verify.txt = 판정 / k6-summary.json = 실행 파라미터) ---
extract_N()    { grep -oE '집계 KPI.* N = [0-9]+' "$1" 2>/dev/null | grep -oE '[0-9]+$' | head -1; }
extract_leak() { grep -oE '돈 보존 누수\(원\) = -?[0-9]+' "$1" 2>/dev/null | grep -oE '\-?[0-9]+$' | head -1; }
extract_json() { grep -oE "\"$2\"[[:space:]]*:[[:space:]]*[0-9]+" "$1" 2>/dev/null | grep -oE '[0-9]+$' | head -1; }

median() { printf '%s\n' "$@" | sort -n | awk '{a[NR]=$0} END{if(NR==0){print "n/a"} else if(NR%2){print a[(NR+1)/2]} else {print (a[NR/2]+a[NR/2+1])/2}}'; }
minv()   { printf '%s\n' "$@" | sort -n | head -1; }
maxv()   { printf '%s\n' "$@" | sort -n | tail -1; }

# --- run 파일 수집 (스모크 등 run 넘버가 없는 라벨은 제외) ---
files=$(ls -1 "results/${PREFIX}-run"*"-verify.txt" 2>/dev/null | sort -V)
if [ -z "$files" ]; then
    echo "[aggregate] results/${PREFIX}-run*-verify.txt 없음 — 먼저 수동 측정 절차로 run 을 쌓을 것" >&2
    exit 1
fi

vals=(); leaks=()
{
    echo "# ADR-008 정합성 측정 집계 — arm=$PREFIX (branch=$BRANCH)"
    echo ""
    echo "| run | ORDER_TARGET | ARRIVAL_RATE | 위반 총수(N/r) | money_leak(원) |"
    echo "|----:|-------------:|-------------:|---------------:|---------------:|"
    for f in $files; do
        label=$(basename "$f" -verify.txt)               # 예: T-run3
        k6="results/${label}-k6-summary.json"
        v=$(extract_N "$f");    v=${v:-"?"}
        l=$(extract_leak "$f"); l=${l:-"?"}
        t="?"; r="?"
        if [ -f "$k6" ]; then
            t=$(extract_json "$k6" order_target); t=${t:-"?"}
            r=$(extract_json "$k6" arrival_rate); r=${r:-"?"}
        fi
        echo "| ${label#"$PREFIX"-run} | $t | $r | $v | $l |"
        [ "$v" != "?" ] && vals+=("$v")
        [ "$l" != "?" ] && leaks+=("$l")
    done
    echo ""
    n=${#vals[@]}
    if [ "$n" -gt 0 ]; then
        echo "**위반 총수**: median=$(median "${vals[@]}")  range=[$(minv "${vals[@]}")..$(maxv "${vals[@]}")]  (n=$n)"
        [ "${#leaks[@]}" -gt 0 ] && \
        echo "**돈 누수(원)**: median=$(median "${leaks[@]}")  range=[$(minv "${leaks[@]}")..$(maxv "${leaks[@]}")]"
        if [ "$n" = "1" ]; then
            echo ""
            echo "> ⚠ **N=1 point estimate** — ADR-008 §재현 하네스 4 는 ≥10회 interleaved 권장."
        fi
    else
        echo "> ⟨측정전⟩ — 파싱 가능한 결과 없음."
    fi
    echo ""
    echo "> KPI 는 이 arm 의 중앙값을 반대편 arm(다른 브랜치) 과 비교: N(control) → r(treatment)."
} > "$AGG"

echo "[aggregate] 완료 → $AGG"
cat "$AGG"
