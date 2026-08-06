#!/usr/bin/env bash
# =============================================================================
# ADR-008 §재현 하네스 2 — 부하 진행도에 앵커한 bounded 고정 카오스 스케줄
#
# 주변 장애 T1–T4 를 "부하 진행도(이터레이션 진행률)"의 서로 다른 지점에 각기 한 번씩 주입한다(bounded).
# 진행률은 drop==0 가정 하에 시간으로 환산한다 — 이터레이션 N 은 t = N ÷ ARRIVAL_RATE 에 출발하므로.
# control·treatment 양쪽에 바이트 동일하게 적용해야 잔여 r 이 공정하게 비교된다.
#
#   진행도 20% → T2  대상 DB 다운:   mysql-order 를 T2_DOWN_S 초 stop 후 start
#   진행도 40% → T3  멱등키 유실:     redis FLUSHALL (idem:order:* 소실 → 같은 키 재전송이 새 주문 이중 생성)
#   진행도 60% → T1  부분 크래시:     orderapi 인스턴스 1개 kill 후 T1_DOWN_S 초 뒤 start (reserveStock↔marker 비원자 창에서 크래시)
#   진행도 80% → T4  kafka 로그 전소: stop orderapi → rm -sf kafka → up -d kafka → start orderapi (볼륨 없어 -v 불필요)
#
# ★ 목적은 "통과 확인"이 아니라 깨지는 지점(복원력 gap) 노출. 넣으면 깨진다 → verify 가 잔여 r 로 계수.
# ★ 무카오스 baseline(= pre-flight 스모크)에서는 이 스크립트를 아예 실행하지 않는다.
#
# 사용: 수동 측정 절차(README §실행)에서 k6 부하 직전에 백그라운드로 띄운다.
#   ORDER_TARGET=100000 bash chaos-schedule.sh > results/$LABEL-chaos.log 2>&1 &
#   CHAOS_PID=$!        # k6 종료 후 kill $CHAOS_PID
# 환경변수: COMPOSE_ARGS(기본 -f docker-compose.qa.yml) POLL(기본 3) T2_DOWN_S(기본 10) T1_DOWN_S(기본 45)
#           FAULTS(기본 T2,T3,T1,T4) — 주입할 폴트 선택. 잔여 r 의 개별 귀속용.
#           HARD_TIMEOUT(기본 부하추정+정착)
#           ARRIVAL_RATE(기본 200) — 앵커 시각 환산에 쓴다. k6 에 준 값과 반드시 같아야 한다.
# =============================================================================
set -uo pipefail
export MSYS_NO_PATHCONV=1
cd "$(dirname "$0")"

# ★ stdin 을 끊는다 — 이 스크립트는 `... &` 로 백그라운드 실행이 전제인데(위 사용법),
#   docker compose exec 는 -T 로도 stdin 을 붙이므로 백그라운드 잡이 터미널을 읽으려다
#   SIGTTIN 을 받아 즉시 "Stopped" 된다. 이 스크립트는 stdin 을 쓰지 않으니 안전하다.
exec 0</dev/null

COMPOSE_ARGS="${COMPOSE_ARGS:--f docker-compose.qa.yml}"
ORDER_TARGET="${ORDER_TARGET:-100000}"
ARRIVAL_RATE="${ARRIVAL_RATE:-200}"
POLL="${POLL:-3}"
T2_DOWN_S="${T2_DOWN_S:-10}"
# ★ 45s 는 Kafka consumer session.timeout.ms 기본값에 맞춘 값 — 줄이지 말 것.
#   kill 된 인스턴스의 멤버는 세션 만료(≈45s)까지 그룹에 남는다. 그 전에 재기동본이 새 멤버로
#   JoinGroup 하면 리밸런스가 좀비의 만료를 기다리며 늘어지고, 기본 RangeAssignor(eager) 라
#   그동안 생존 인스턴스까지 파티션을 반납한 채 대기 → 그룹 전체 소비 정지.
#   sleep 을 45s 로 두면 컨테이너 기동 자체가 만료 이후라 부팅 시간과 무관하게 이 구간을 피한다.
#   (T1 의 피해량은 kill 순간 reserveStock↔마커 창에서 확정되므로 이 값과 무관 — 처리량만의 문제.)
T1_DOWN_S="${T1_DOWN_S:-45}"
# 종료 보증(T4 앵커에만 의존하지 않음): 측정자가 k6 종료 후 kill 하는 게 기본이고, 놓치더라도
# (1) 진행도 정체 감지(STALL_LIMIT) (2) 부하추정+정착 HARD_TIMEOUT 이 이중으로 종료를 보장.
HARD_TIMEOUT="${HARD_TIMEOUT:-$(( ORDER_TARGET / ARRIVAL_RATE + 300 ))}"
STALL_LIMIT="${STALL_LIMIT:-40}"     # 진행도 무변화 40*POLL(≈120s) → 남은 앵커 생략하고 종료
SCOPE="username LIKE 'ctrich%'"

# ★ 주입할 폴트 선택 — 잔여 r 의 개별 귀속을 위해 일부만 넣을 수 있다.
#   예) FAULTS=T2,T3,T1  (T4 제외)   FAULTS=T4  (단독 주입)
#   ADR 은 4종 전부를 bounded 로 넣는 것을 기본으로 하므로 기본값은 전체다.
FAULTS="${FAULTS:-T2,T3,T1,T4}"
enabled() { case ",$FAULTS," in *",$1,"*) return 0 ;; *) return 1 ;; esac; }

progress() {
    docker compose $COMPOSE_ARGS exec -T mysql-order mysql -uroot -proot -N -B orders \
        -e "SELECT COUNT(*) FROM orders WHERE ($SCOPE);" 2>/dev/null | tr -d '[:space:]'
}
# ★ 앵커는 "이터레이션 진행도"로 잡고 시간으로 환산한다.
#   drop==0 가정 하에 이터레이션 N 은 t = N ÷ ARRIVAL_RATE 에 출발하므로,
#   진행도 P% 는 부하 시작 후 (P% × ORDER_TARGET ÷ ARRIVAL_RATE) 초에 해당한다.
sec_at() { echo $(( ORDER_TARGET * $1 / 100 / ARRIVAL_RATE )); }
now() { date +%s; }
log() { echo "[chaos $(date -u +%H:%M:%S)] $*"; }

S20=$(sec_at 20); S40=$(sec_at 40); S60=$(sec_at 60); S80=$(sec_at 80)

did_t2=0; did_t3=0; did_t1=0; did_t4=0
last_p=-1; stall=0
boot=$(now)

log "스케줄 앵커(부하 시작 후 초): T2@${S20}s  T3@${S40}s  T1@${S60}s  T4@${S80}s"
log "  = 이터레이션 진행도 20/40/60/80% (target=$ORDER_TARGET ÷ rate=$ARRIVAL_RATE, drop=0 가정), hard_timeout=${HARD_TIMEOUT}s"
log "  주입 대상: $FAULTS"

# 부하 시작(첫 주문) 대기 — 여기부터가 t=0. k6 setup 구간만큼의 오차를 없앤다.
start=""
while [ -z "$start" ] && [ $(( $(now) - boot )) -lt "$HARD_TIMEOUT" ]; do
    p=$(progress); case "$p" in ''|*[!0-9]*) p=0 ;; esac
    if [ "$p" -gt 0 ]; then
        start=$(now)
        log "부하 시작 감지 (주문 $p건) — 여기부터 t=0"
    else
        sleep "$POLL"
    fi
done
[ -z "$start" ] && { log "⚠ 부하 시작을 감지하지 못함 — 종료"; exit 1; }

while [ $(( $(now) - start )) -lt "$HARD_TIMEOUT" ]; do
    p=$(progress); case "$p" in ''|*[!0-9]*) p=0 ;; esac
    t=$(( $(now) - start ))

    if enabled T2 && [ "$did_t2" = 0 ] && [ "$t" -ge "$S20" ]; then
        did_t2=1
        log "T2 주입 — mysql-order ${T2_DOWN_S}s 다운 (t=${t}s, 진행도 $p건)"
        docker compose $COMPOSE_ARGS stop mysql-order >/dev/null 2>&1
        sleep "$T2_DOWN_S"
        docker compose $COMPOSE_ARGS start mysql-order >/dev/null 2>&1
        log "T2 완료 — mysql-order 복구"
    fi

    if enabled T3 && [ "$did_t3" = 0 ] && [ "$t" -ge "$S40" ]; then
        did_t3=1
        log "T3 주입 — redis FLUSHALL (멱등키 유실, t=${t}s, 진행도 $p건)"
        docker compose $COMPOSE_ARGS exec -T redis redis-cli FLUSHALL >/dev/null 2>&1
        log "T3 완료"
    fi

    if enabled T1 && [ "$did_t1" = 0 ] && [ "$t" -ge "$S60" ]; then
        did_t1=1
        cid=$(docker compose $COMPOSE_ARGS ps -q orderapi 2>/dev/null | head -1)
        if [ -n "$cid" ]; then
            log "T1 주입 — orderapi 인스턴스 1개 kill ($cid, t=${t}s, 진행도 $p건)"
            docker kill "$cid" >/dev/null 2>&1
            # ★ T2·T4 와 같은 bounded 카오스: 크래시 후 되살린다(운영에선 오케스트레이터가 재기동).
            #   노리는 피해(reserveStock 커밋 ↔ 마커 커밋 사이 크래시 → PaymentDeducted 재배달 → 재실행)는
            #   오프셋 미커밋 때문에 일어나므로 인스턴스가 돌아와도 그대로 발생한다.
            #   죽은 채로 두면 남은 런 내내 용량이 1/4 빠져(실측 order_create failed 25%) 뒤 앵커 도달만 막는다.
            sleep "$T1_DOWN_S"
            docker start "$cid" >/dev/null 2>&1
            log "T1 완료 — ${T1_DOWN_S}s 후 인스턴스 재기동 (크래시 창은 이미 통과)"
        else
            log "T1 skip — orderapi 컨테이너를 찾지 못함"
        fi
    fi

    if enabled T4 && [ "$did_t4" = 0 ] && [ "$t" -ge "$S80" ]; then
        did_t4=1
        log "T4 주입 — kafka 로그 전소 재생성 (t=${t}s, 진행도 $p건)"
        docker compose $COMPOSE_ARGS stop orderapi >/dev/null 2>&1
        docker compose $COMPOSE_ARGS rm -sf kafka  >/dev/null 2>&1
        docker compose $COMPOSE_ARGS up -d kafka    >/dev/null 2>&1
        sleep 20
        docker compose $COMPOSE_ARGS start orderapi >/dev/null 2>&1   # ★ start(재구동) — up 아님: control 오버레이 env 보존
        log "T4 완료 — kafka 로그·오프셋 전소 후 재기동"
        break   # 마지막 앵커까지 주입 완료
    fi

    # 진행도 정체(부하 종료/정지) 감지 — 남은 앵커가 있어도 종료 (control/degraded arm 무한 대기 방지).
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
