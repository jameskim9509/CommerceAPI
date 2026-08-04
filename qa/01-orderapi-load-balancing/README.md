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
| 반복 단위       | `cart_add → order`                                                     |
| 측정 지표       | 주문 응답 p50/p95/**p99**, throughput,(초당 완료주문수) 에러율      |
| 보조 지표       | 인스턴스 별 CPU 사용률, MySQL 스레드 부하                                 |
| 실험            | 인스턴스 x 1 / 인스턴스 x 2 / 인스턴스 x 4                            |
| 인스턴스당 자원 | 2 CPU / 1 GB                                                              |

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

# 3) 시드 + 레지스트리 전파 대기
docker compose -f docker-compose.qa.yml wait db-seed
# Eureka 등록 → 서버 응답캐시(30s) → gateway fetch(30s) → LB 캐시(35s) 로 전파가
# 최악 90 초 이상 걸린다. 고정 sleep 대신 registry 를 폴링해 N 개가 UP 인지 확인한다.
# (전파 전에 부하를 걸면 조용히 N=1 을 측정하게 된다)
until [ "$(curl -s -H 'Accept: application/json' http://localhost:8761/eureka/apps \
        | grep -o '"instanceId":"order-api' | wc -l)" -ge "$N" ] \
   && [ "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/order/customer/cart)" = "403" ]; do
    sleep 5
done

# 4) 모니터링 백그라운드 → results/$LABEL-*.csv에 기록
bash monitor-stats.sh $LABEL &

# 5) 워밍업 런 (결과 버림) — JIT·HikariCP·Hibernate 캐시가 데워지기 전 값은 2 배 이상 느리다
EXPERIMENT_LABEL=warmup VUS=500 DURATION=2m \
    docker compose -f docker-compose.qa.yml run --rm --no-deps k6 run /scripts/load-test.js

# 6) 본 측정 (--no-deps 필수 — 없으면 depends_on 재조정으로 orderapi 가 1 개로 스케일다운된다)
EXPERIMENT_LABEL=$LABEL VUS=500 DURATION=5m \
    docker compose -f docker-compose.qa.yml run --rm --no-deps \
    k6 run --summary-export /results/$LABEL-k6-summary.json /scripts/load-test.js

# 7) 모니터 종료
rm -f /tmp/qa-monitor.lock

# 8) 정리 후 다음 N 으로
docker compose -f docker-compose.qa.yml down -v
```

**N = 1, 2, 4 로 세 번 반복**한 뒤 분석:

```bash
python parse_results.py        # 측정 지표 분석
python analyze_monitoring.py   # 보조 지표 분석
```

## 측정

인스턴스를 늘렸을 때 **얼마나 개선됐는지**를 1개 인스턴스기준의 상대값으로 기록한다.

- p99 감소율 — `(p99 of 1_instance − p99 of N_instance) / p99 of 1_instance` × 100 %
- throughput 증가율 — `(tps of N_instance − tps of 1_instance) / tps of 1_instance` × 100 %

> tps (transaction per second): 초당 주문 완료수, 한 주문 트랜잭션 단위를 장바구니 추가 -> 주문 1건으로 책정한다.

**측정 유효성 가드**

- 인스턴스별 요청 균등 분배 — orderapi 인스턴스 CPU 최대 편차 ≤10%
- 낮은 에러율 — `order 실패 수 / order 전체 수` × 100 < 1%

### 보조 측정

- MySQL 포화 — 평균 스레드 수 / 최대 스레드 수
- MySQL 처리량 — 초당 처리한 쿼리 수
