#!/usr/bin/env bash
# =============================================================================
# ADR-008 §재현 하네스 2 — 부하 진행도에 앵커한 bounded 고정 카오스 스케줄
#
# 주변 장애 T1–T4 를 "부하 진행도(스코프 내 생성 주문 수 / ORDER_TARGET)"의 서로 다른 지점에 각기 한 번씩
# 주입한다(bounded). control·treatment 양쪽에 바이트 동일하게 적용해야 잔여 r 이 공정하게 비교된다.
#
#   진행도 20% → T2  대상 DB 다운:   mysql-order 를 T2_DOWN_S 초 stop 후 start (소비 중 재시도창 초과 → 이벤트 드롭)
#   진행도 40% → T3  멱등키 유실:     redis FLUSHALL (idem:order:* 소실 → 같은 키 재전송이 새 주문 이중 생성)
#   진행도 60% → T1  부분 크래시:     orderapi 인스턴스 1개 kill (reserveStock↔marker 비원자 창에서 크래시)
#   진행도 80% → T4  kafka 로그 전소: stop orderapi → rm -sf kafka → up -d kafka → start orderapi (볼륨 없어 -v 불필요)
#
# ★ 목적은 "통과 확인"이 아니라 깨지는 지점(복원력 gap) 노출. 넣으면 깨진다 → verify 가 잔여 r 로 계수.
# ★ CHAOS=off (run-consistency.sh) 이면 이 스크립트는 호출되지 않는다 → pre-flight 스모크/무카오스 baseline 용.
#
# 사용: COMPOSE_ARGS="-f docker-compose.qa.yml" ORDER_TARGET=100000 ./chaos-schedule.sh
# 환경변수: POLL(기본 3) T2_DOWN_S(기본 25) HARD_TIMEOUT(기본 부하추정+정착)
# =============================================================================
set -uo pipefail
export MSYS_NO_PATHCONV=1
cd "$(dirname "$0")"

COMPOSE_ARGS="${COMPOSE_ARGS:--f docker-compose.qa.yml}"
ORDER_TARGET="${ORDER_TARGET:-100000}"
ARRIVAL_RATE="${ARRIVAL_RATE:-200}"
POLL="${POLL:-3}"
T2_DOWN_S="${T2_DOWN_S:-25}"
# 종료 보증(A80 앵커에만 의존하지 않음): run-consistency 는 부하 종료 후 grace 뒤 이 프로세스를 kill 하고,
# 독립 실행 시엔 (1) 진행도 정체 감지(STALL_LIMIT) (2) 부하추정+정착 HARD_TIMEOUT 이 이중으로 종료를 보장.
HARD_TIMEOUT="${HARD_TIMEOUT:-$(( ORDER_TARGET / ARRIVAL_RATE + 300 ))}"
STALL_LIMIT="${STALL_LIMIT:-40}"     # 진행도 무변화 40*POLL(≈120s) → 남은 앵커 생략하고 종료
SCOPE="username LIKE 'ctrich%'"

progress() {
    docker compose $COMPOSE_ARGS exec -T mysql-order mysql -uroot -proot -N -B orders \
        -e "SELECT COUNT(*) FROM orders WHERE ($SCOPE);" 2>/dev/null | tr -d '[:space:]'
}
pct() { echo $(( ORDER_TARGET * $1 / 100 )); }
now() { date +%s; }
log() { echo "[chaos $(date -u +%H:%M:%S)] $*"; }

A20=$(pct 20); A40=$(pct 40); A60=$(pct 60); A80=$(pct 80)
did_t2=0; did_t3=0; did_t1=0; did_t4=0
last_p=-1; stall=0
start=$(now)

log "스케줄 앵커(주문 수): T2@$A20  T3@$A40  T1@$A60  T4@$A80  (target=$ORDER_TARGET, hard_timeout=${HARD_TIMEOUT}s)"

while [ $(( $(now) - start )) -lt "$HARD_TIMEOUT" ]; do
    p=$(progress); case "$p" in ''|*[!0-9]*) p=0 ;; esac

    if [ "$did_t2" = 0 ] && [ "$p" -ge "$A20" ]; then
        did_t2=1
        log "T2 주입 — mysql-order ${T2_DOWN_S}s 다운 (진행도 $p)"
        docker compose $COMPOSE_ARGS stop mysql-order >/dev/null 2>&1
        sleep "$T2_DOWN_S"
        docker compose $COMPOSE_ARGS start mysql-order >/dev/null 2>&1
        log "T2 완료 — mysql-order 복구"
    fi

    if [ "$did_t3" = 0 ] && [ "$p" -ge "$A40" ]; then
        did_t3=1
        log "T3 주입 — redis FLUSHALL (멱등키 유실, 진행도 $p)"
        docker compose $COMPOSE_ARGS exec -T redis redis-cli FLUSHALL >/dev/null 2>&1
        log "T3 완료"
    fi

    if [ "$did_t1" = 0 ] && [ "$p" -ge "$A60" ]; then
        did_t1=1
        cid=$(docker compose $COMPOSE_ARGS ps -q orderapi 2>/dev/null | head -1)
        if [ -n "$cid" ]; then
            log "T1 주입 — orderapi 인스턴스 1개 kill ($cid, 진행도 $p)"
            docker kill "$cid" >/dev/null 2>&1
            log "T1 완료 — 인스턴스 1개 소실(용량 감소는 의도된 bounded 카오스)"
        else
            log "T1 skip — orderapi 컨테이너를 찾지 못함"
        fi
    fi

    if [ "$did_t4" = 0 ] && [ "$p" -ge "$A80" ]; then
        did_t4=1
        log "T4 주입 — kafka 로그 전소 재생성 (진행도 $p)"
        docker compose $COMPOSE_ARGS stop orderapi >/dev/null 2>&1
        docker compose $COMPOSE_ARGS rm -sf kafka  >/dev/null 2>&1
        docker compose $COMPOSE_ARGS up -d kafka    >/dev/null 2>&1
        sleep 20
        docker compose $COMPOSE_ARGS start orderapi >/dev/null 2>&1   # ★ start(재구동) — up 아님: control 오버레이 env 보존
        log "T4 완료 — kafka 로그·오프셋 전소 후 재기동"
        break   # 마지막 앵커까지 주입 완료
    fi

    # 진행도 정체(부하 종료/정지) 감지 — A80 앵커 미도달이어도 종료 (control/degraded arm 무한 대기 방지).
    #   p=0(예: T2 로 mysql 다운 → 조회 불가)일 땐 정체로 세지 않는다.
    if [ "$p" -gt 0 ] && [ "$p" -le "$last_p" ]; then
        stall=$(( stall + 1 ))
    else
        stall=0
    fi
    last_p="$p"
    if [ "$stall" -ge "$STALL_LIMIT" ]; then
        log "진행도 정체 감지 (p=$p, ${STALL_LIMIT}회 무변화) — 남은 앵커 생략하고 종료 (T2=$did_t2 T3=$did_t3 T1=$did_t1 T4=$did_t4)"
        break
    fi

    sleep "$POLL"
done

log "카오스 스케줄 종료 (T2=$did_t2 T3=$did_t3 T1=$did_t1 T4=$did_t4)"
