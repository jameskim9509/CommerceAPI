# 시나리오 01 — orderApi 부하 분산

> 상위 개요: [../README.md](../README.md)

orderApi 인스턴스를 1 / 2 / 4 로 늘리면서 Eureka LoadBalancer 의 **orderApi 부하 분산 효과**를 측정한다.

## 디렉토리 구조

```
qa/01-orderapi-load-balancing/
├── docker-compose.qa.yml           QA 스택
├── monitor-stats.sh                5초 간격 보조 지표 캡쳐 스크립트
├── parse_results.py                측정 지표 분석 스크립트
├── analyze_monitoring.py           보조 지표 분석 스크립트
├── seed/                           시드값
│   ├── user.sql  
│   └── order.sql   
├── k6/
│   └── load-test.js                시나리오 스크립트
└── results/                        산출물
```

## 사전 요구사항

- Docker & compose
- 약 8 GB 메모리 + 8 CPU
- WSL2 (Windows) / Bash (Linux)

## 측정 모델 요약

| 항목            | 값                                                                        |
| --------------- | ------------------------------------------------------------------------- |
| 측정 대상       | `POST /order/customer/cart, POST order/customer/cart/order` 응답 시간  |
| 워크로드        | **constant-vus** — 500 VUs 를 5 분간 유지 (총 주문 수는 결과값)     |
| 워크로드        | **shared-iterations** — 500 VUs 로 주문 5 만 건 버스트 (런 시간은 결과값) |
| 반복 단위       | `cart_add → order` (VU:유저 1:1, VU:상품 1:1)                          |
| 측정 지표       | **버스트 소진 시간**, 주문 응답 p50/**p95**/p99, 에러율                 |
| 실험            | 인스턴스 x 1 / 인스턴스 x 2 / 인스턴스 x 4                            |
| 인스턴스당 자원 | 2 CPU / 1 GB                                                              |


`EXECUTOR` 로 두 부하 모델을 고른다. 둘 다 closed-loop 다 — VU 가 응답을 받아야 다음 요청을
보내므로 요청 백로그가 VU 수를 넘지 않는다 (백로그가 쌓이는 건 `constant-arrival-rate`).

| EXECUTOR | 고정하는 것 | 결과값 | 쓰는 곳 |
| --- | --- | --- | --- |
| `shared-iterations` (기본) | 총 주문 수 `ORDER_COUNT` | 런 시간 | **버스트 흡수 능력** — 소진 시간 비교 |
| `constant-vus` | 런 시간 `DURATION` | 총 주문 수 | 정상 상태 처리량 비교 |

> `shared-iterations` 는 공용 풀이 비면서 동시성이 500 → 0 으로 떨어지는 꼬리가 생겨
> k6 가 보고하는 `throughput(orders/s)` 을 과소평가한다 (같은 조건에서 -18% 관측).
> **버스트 비교에는 rate 가 아니라 소진 시간(`run_duration_ms`)을 쓴다.**

## 실행

인스턴스를 **1 → 2 → 4 로 직접 늘려가며** 각각 측정한다. 각 N 마다 아래 절차를 반복한다.

```bash
cd qa/01-orderapi-load-balancing
N=2                         # 이번 실험 인스턴스 수 (1 → 2 → 4)
LABEL=${N}_instance         # 결과 접두사 (1_instance / 2_instance / 4_instance)  
export ORDERAPI_REPLICAS=$N # compose deploy.replicas 로 주입

# 1) 이전 잔재 정리
docker compose -f docker-compose.qa.yml down -v

# 2) QA 스택 기동 (k6 제외)
docker compose -f docker-compose.qa.yml up -d \
    mysql-user mysql-order redis kafka eureka userapi orderapi gateway db-seed

# 3) 시드 완료 확인 — exit 0 이어야 한다.
#    Flyway 레이스로 실패하면(exit 1) 상품이 없어 cart_add 가 전건 실패하는데,
#    주문 요청 자체가 0 건이라 k6 는 "order fail 0.00%" 로 보고해 조용히 지나간다.
until [ "$(docker inspect -f '{{.State.Status}}:{{.State.ExitCode}}' \
        qa-orderapi-load-balancing-db-seed-1 2>/dev/null)" = "exited:0" ]; do
    sleep 5
done

# 4) 레지스트리 전파 대기
#    Eureka 등록 → 서버 응답캐시(30s) → gateway fetch(30s) → LB 캐시(35s) 로 전파가
#    최악 90 초 이상 걸린다. 고정 sleep 대신 registry 를 폴링해 N 개가 UP 인지 확인한다.
#    (전파 전에 부하를 걸면 조용히 N=1 을 측정하게 된다)
until [ "$(curl -s -H 'Accept: application/json' http://localhost:8761/eureka/apps \
        | grep -o '"instanceId":"order-api' | wc -l)" -ge "$N" ] \
   && [ "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/order/customer/cart)" = "403" ]; do
    sleep 5
done

# 5) 워밍업 런 (결과 버림) — JIT·HikariCP·Hibernate 캐시가 데워지기 전 값은 2 배 이상 느리다
EXPERIMENT_LABEL=warmup VUS=500 EXECUTOR=shared-iterations ORDER_COUNT=20000 \
    docker compose -f docker-compose.qa.yml run --rm --no-deps k6 run /scripts/load-test.js

# 6) SAGA 정지 대기 — 워밍업이 만든 백로그가 다 소진될 때까지 (약 6~8 분)
#    비동기 파이프라인은 open-loop 라 접수(약 180~400/s)가 완료(약 48/s)를 앞질러
#    백로그가 무한히 쌓인다. 남겨둔 채 측정하면 배경 부하가 구성마다 달라진다.
until [ "$(docker exec qa-orderapi-load-balancing-mysql-order-1 \
        mysql -uroot -proot orders -N -e \
        "SELECT COUNT(*) FROM orders WHERE status='PENDING'")" = "0" ]; do
    sleep 20
done

# 7) 모니터링 시작 → results/$LABEL-*.csv 에 기록
#    ★ 반드시 정지 대기 "뒤에" 켠다. 앞에서 켜면 워밍업 + 유휴 구간이 CSV 에 섞여
#      analyze_monitoring.py 의 CPU 평균이 희석된다.
rm -f /tmp/qa-monitor.lock
bash monitor-stats.sh $LABEL &

# 8) 본 측정 (--no-deps 필수 — 없으면 depends_on 재조정으로 orderapi 가 1 개로 스케일다운된다)
EXPERIMENT_LABEL=$LABEL VUS=500 EXECUTOR=shared-iterations ORDER_COUNT=50000 \
    docker compose -f docker-compose.qa.yml run --rm --no-deps \
    k6 run --summary-export /results/$LABEL-k6-summary.json /scripts/load-test.js

# 9) 모니터 종료
rm -f /tmp/qa-monitor.lock

# 10) 유효성 가드 확인 — ★ down -v 전에. DB 를 지우면 확인할 수 없다.
#     (a) 전 인스턴스 트래픽 수신: Threads_connected = 10N + 1
docker exec qa-orderapi-load-balancing-mysql-order-1 mysql -uroot -proot -N -e \
    "SELECT VARIABLE_VALUE FROM performance_schema.global_status
      WHERE VARIABLE_NAME='Threads_connected'"
#     (b) SAGA 동작: FAILED 0 · CONFIRMED > 0
#         측정 직후 PENDING 이 많이 남는 건 정상이다 (접수가 완료보다 4 배 빠르다).
#         FAILED 가 잡히면 시드의 customer_balance_history 누락을 의심한다.
docker exec qa-orderapi-load-balancing-mysql-order-1 mysql -uroot -proot orders -e \
    "SELECT status, COUNT(*) FROM orders GROUP BY status"
#     (c) 인스턴스별 CPU 편차 · MySQL 부하
python analyze_monitoring.py

# 11) 정리 후 다음 N 으로
docker compose -f docker-compose.qa.yml down -v
```

> **Windows Git Bash 로 실행할 경우** 경로 변환 때문에 `/scripts/load-test.js` 가
> `C:/Program Files/Git/scripts/load-test.js` 로 바뀌어 k6 가 파일을 못 찾는다.
> 5·8 단계 앞에 `export MSYS_NO_PATHCONV=1` 을 두거나 WSL2 에서 실행한다.

**N = 1, 2, 4 로 세 번 반복**한 뒤 분석:

```bash
python parse_results.py        # 측정 지표 분석
python analyze_monitoring.py   # 보조 지표 분석
```

## 측정

인스턴스를 늘렸을 때 **얼마나 개선됐는지**를 1개 인스턴스 기준의 상대값으로 기록한다.

- **소진 시간 단축 배율** — `(소진 시간 of 1_instance) / (소진 시간 of N_instance)` — 버스트 흡수 능력
- **p95 감소율** — `(p95 of 1_instance − p95 of N_instance) / p95 of 1_instance` × 100 % — 사용자 체감 지연
- 소진 시간 효율 — `단축 배율 / N` × 100 % — 이상적 선형 확장(N 배) 대비 달성률

> 소진 시간은 요약의 `run_duration_ms` 를 쓴다. setup(로그인 500 회) 시간이 포함되므로
> 구성 간 비교 시 `USER_COUNT` 가 동일해야 한다.

**측정 유효성 가드**

- **인스턴스별 요청 균등 분배** — orderapi 인스턴스 CPU 최대 편차 ≤10%
- **전 인스턴스 트래픽 수신** — MySQL `Threads_connected` = 10N+1 (HikariCP 기본 풀 10 이 인스턴스마다
  lazy-init 됨). CPU 와 독립된 교차 검증 지표이며, 과거 `docker compose run` 이 `--scale` 을
  리셋해 조용히 N=1 을 측정하던 버그를 이 지표가 잡아냈다.
- 낮은 에러율 — `order 실패 수 / order 전체 수` × 100 < 1%
- **SAGA 완주 확인** — 측정 후 `orders` 의 `CONFIRMED` 건수 = 주문 수, `FAILED` 0.
  k6 는 HTTP 200(PENDING 생성 성공)만 보므로 결제·재고 단계가 전건 실패해도 에러율 0% 로 보고된다.

### 보조 측정

- MySQL 포화 — 평균 스레드 수 / 최대 스레드 수
- MySQL 처리량 — 초당 처리한 쿼리 수
