#!/usr/bin/env bash
# =============================================================================
# ADR-008 §측정 계획 — 무방어(control) vs 방어(treatment) 집계 KPI 매트릭스
#
# 같은 부하·같은 bounded 고정 카오스 스케줄 아래 control 과 treatment 를 N회 interleaved 반복:
#   control(무방어)  → desired 위반 대량 + 카오스 잔여 ≈ N
#   treatment(현재)  → desired 방어(→0) + 카오스 잔여 ≈ r
#   KPI = N → r   (감소분 = 6개 정합성 방어가 실제로 커버한 부분; r>0 은 주변 장애 T1–T4 잔여)
#
# ADR-008 §재현 하네스 4: 점추정 금지 — 전 run 원값·중앙값·범위를 공표. control 이 cascade 로 대부분
# PENDING 붕괴한 run 은 VOID(baseline 부적격)로 표기.
#
# 사용: ./run-kpi-matrix.sh [N=10]
# 환경변수는 run-consistency.sh 로 전달됨 (ORDER_TARGET, ARRIVAL_RATE, N_ORDERAPI, CHAOS ...).
# =============================================================================
set -uo pipefail
export MSYS_NO_PATHCONV=1
cd "$(dirname "$0")"

N="${1:-10}"
mkdir -p results
MATRIX="results/KPI-MATRIX.md"

echo "[matrix] 이미지 빌드 (treatment + control) ..."
./build-images.sh
./control/build-control-images.sh

echo "[matrix] $N회 interleaved 실행 시작 ..."
for i in $(seq 1 "$N"); do
    echo "===== run $i/$N ====="
    bash run-consistency.sh treatment "T-run$i" || true
    bash run-consistency.sh control   "C-run$i" || true
done

# --- 집계 (verify.txt 에서 N·money_leak 파싱) ---
extract_N()    { grep -oE '집계 KPI.* N = [0-9]+' "$1" 2>/dev/null | grep -oE '[0-9]+$' | head -1; }
extract_leak() { grep -oE '돈 보존 누수\(원\) = -?[0-9]+' "$1" 2>/dev/null | grep -oE '\-?[0-9]+$' | head -1; }

median() { printf '%s\n' "$@" | sort -n | awk '{a[NR]=$0} END{if(NR==0){print "n/a"} else if(NR%2){print a[(NR+1)/2]} else {print (a[NR/2]+a[NR/2+1])/2}}'; }
minv()   { printf '%s\n' "$@" | sort -n | head -1; }
maxv()   { printf '%s\n' "$@" | sort -n | tail -1; }

t_vals=(); c_vals=()
{
    echo "# ADR-008 정합성 KPI 매트릭스 (무방어 N → 방어 r)"
    echo ""
    echo "- 실행 파라미터: ORDER_TARGET=${ORDER_TARGET:-100000}, ARRIVAL_RATE=${ARRIVAL_RATE:-200}/s, N_ORDERAPI=${N_ORDERAPI:-4}, CHAOS=${CHAOS:-on}, N=$N"
    echo ""
    echo "| run | treatment r (위반 N) | control N (위반 N) | t money_leak(원) | c money_leak(원) |"
    echo "|----:|---------------------:|-------------------:|-----------------:|-----------------:|"
    for i in $(seq 1 "$N"); do
        tf="results/T-run$i-verify.txt"; cf="results/C-run$i-verify.txt"
        tn=$(extract_N "$tf");   tn=${tn:-"?"}
        cn=$(extract_N "$cf");   cn=${cn:-"?"}
        tl=$(extract_leak "$tf"); tl=${tl:-"?"}
        cl=$(extract_leak "$cf"); cl=${cl:-"?"}
        echo "| $i | $tn | $cn | $tl | $cl |"
        [ "$tn" != "?" ] && t_vals+=("$tn")
        [ "$cn" != "?" ] && c_vals+=("$cn")
    done
    echo ""
    if [ "${#t_vals[@]}" -gt 0 ] && [ "${#c_vals[@]}" -gt 0 ]; then
        echo "## 요약"
        echo ""
        echo "- treatment r: median=$(median "${t_vals[@]}")  range=[$(minv "${t_vals[@]}")..$(maxv "${t_vals[@]}")]  (n=${#t_vals[@]})"
        echo "- control   N: median=$(median "${c_vals[@]}")  range=[$(minv "${c_vals[@]}")..$(maxv "${c_vals[@]}")]  (n=${#c_vals[@]})"
        echo "- **KPI: N($(median "${c_vals[@]}")) → r($(median "${t_vals[@]}"))** (중앙값 기준; 개별 귀속 없는 집계 주장)"
    else
        echo "> ⟨측정전⟩ — 아직 실행 결과 없음. 이 매트릭스는 run-kpi-matrix.sh 를 실제로 돌리면 채워진다."
    fi
    echo ""
    echo "> 주의: r>0 은 6개 방어 대상이 아닌 주변 장애 T1–T4 가 양쪽 arm 에 똑같이 남기 때문."
    echo "> control 이 cascade 로 대부분 PENDING 붕괴한 run 은 VOID(baseline 부적격) — 수기 확인 후 제외."
} > "$MATRIX"

echo "[matrix] 완료 → $MATRIX"
cat "$MATRIX"
