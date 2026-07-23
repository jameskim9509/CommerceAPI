# 시나리오 01 — 부하 분산 (ADR-005 시나리오 3)

> 상위 개요: [../README.md](../README.md) · 정합성 통합 시나리오(02-consistency)는 [ADR-008](../../ADR/008-order-consistency-integration-scenario.md) 명세대로 신규 작성 예정.

orderApi 인스턴스를 1 / 2 / 4 로 늘리면서 Gateway + Eureka LoadBalancer 의
부하 분산 효과를 정량 측정한다. [ADR-005](../../ADR/005-eureka-gateway-load-balancing.md)
의 "시나리오 3" 측정 설계를 그대로 구현.

## 디렉토리 구조

```
qa/01-load-balancing/
├── docker-compose.qa.yml           측정 전용 compose (작은 자원 한도 + k6 runner + db-seed). name: qa-load-balancing 고정
├── monitor-stats.sh                부하 중 docker stats + MySQL + Eureka 를 5초 간격 캡쳐 (측정 중 백그라운드로 직접 실행)
├── parse_results.py                k6 summary → 핵심 지표 표
├── analyze_bottleneck.py           엔드포인트별 latency 분해 (병목 식별)
├── analyze_monitoring.py           CPU/MySQL 모니터링 CSV → avg/p95/max
├── seed/                           이 시나리오의 자립 시드 (자기 seller + 부하 더미)
│   ├── user.sql                    seller1(id=1) + 1000 customer (customer{i}@qa.test, verify=true)
│   └── order.sql                   100 product × 5 product_item (id 1..500, 재고 1M, seller_id=1)
├── k6/
│   ├── load-test.js                로그인→카트→주문, ramping-vus 0→200, 5 분
│   └── load-test-order-only.js     카트 사전적재 후 order 만 (burst)
└── results/                        실행 결과 (E1·E2·E3·E*o JSON / 서버측 메트릭 / MEASUREMENT_REPORT.md)
```

## 사전 요구사항

- Docker daemon 실행 중
- 약 8 GB 메모리 + 8 CPU (4 인스턴스 + MySQL × 2 + Kafka 등 동시 기동)
- Linux/macOS 또는 WSL2 (bash 스크립트)

## 측정 모델 요약

| 항목 | 값 |
|---|---|
| 측정 대상 | `POST /order/customer/cart/order` 응답 시간 |
| 워크로드 | ramping-vus, 0 → 50 (30s) → 200 (1m) → 200 (3m steady) → 0 (30s) |
| 측정 지표 | p50/p95/**p99** latency, throughput, 에러율, 인스턴스별 분배, 멱등성 위반 |
| 보조 | SAGA end-to-end (orders.created_date ↔ modified_date), Outbox 잔여, docker stats |
| 실험 | E1 (1 인스턴스) / E2 (2) / E3 (4) |
| 인스턴스당 자원 | 2 CPU / 1 GB (관측 가능한 차이를 만들기 위해 의도적으로 작게) |

## 실행 — 수동 측정

인스턴스를 **1 → 2 → 4 로 직접 늘려가며** 각각 측정한다. 각 N 마다 아래 절차를 반복한다.
(Windows Git Bash 는 컨테이너 경로 보호를 위해 `export MSYS_NO_PATHCONV=1` 를 먼저; WSL2 는 불필요.)

```bash
cd qa/01-load-balancing
N=2                # 이번 실험 인스턴스 수 (1 → 2 → 4)
LABEL=E2           # 결과 접두사 (E1/E2/E3; order-only 는 E1o/E2o/E3o)

# 1) 이전 잔재 정리
docker compose -f docker-compose.qa.yml down -v --remove-orphans

# 2) 스택 기동 (db-seed 제외 — 시드는 4)에서 직접 주입)
docker compose -f docker-compose.qa.yml up -d --scale orderapi=$N \
    mysql-user mysql-order redis kafka eureka userapi orderapi gateway

# 3) ready 대기 (JVM 부팅 + Eureka 등록 + Gateway registry fetch)
sleep 60

# 4) 자립 시드 주입 (user.sql → order.sql 순서: order 의 seller_id=1 이 user 의 seller 참조)
docker compose -f docker-compose.qa.yml exec -T mysql-user  mysql -uroot -proot user   < seed/user.sql
docker compose -f docker-compose.qa.yml exec -T mysql-order mysql -uroot -proot orders < seed/order.sql

# 5) (선택) 모니터링 백그라운드 — docker stats + MySQL + Eureka 5초 간격 → results/$LABEL-*.csv
bash monitor-stats.sh $LABEL &

# 6) k6 부하 (★ --no-deps 필수: 없으면 --scale 이 1 로 리셋)
#    전체 흐름 = /scripts/load-test.js   ·   order-only = /scripts/load-test-order-only.js (LABEL 도 E2o 로)
EXPERIMENT_LABEL=$LABEL docker compose -f docker-compose.qa.yml run --rm --no-deps k6 \
    run --summary-export /results/$LABEL-k6-summary.json /scripts/load-test.js

# 7) 모니터 종료 (lock 파일 제거)
rm -f /tmp/qa-monitor.lock

# 8) (선택) 안정화 대기 후 서버측 지표 (Outbox 처리 + SAGA 완결)
sleep 60
docker compose -f docker-compose.qa.yml exec -T mysql-order mysql -uroot -proot -e "
    SELECT status, COUNT(*) AS cnt FROM orders.orders GROUP BY status;          -- 주문 상태 분포 (PENDING=0 확인)
    SELECT COUNT(*) AS unsent FROM orders.outbox_events WHERE sent_at IS NULL;  -- Outbox 미발행 잔여
"

# 9) 정리 후 다음 N 으로
docker compose -f docker-compose.qa.yml down -v
```

**N = 1, 2, 4 로 세 번 반복**한 뒤 결과를 분석한다:

```bash
python parse_results.py        # k6 summary → p50/p95/p99·throughput·에러율·분배 표
python analyze_monitoring.py   # CPU/MySQL 모니터링 CSV → avg/p95/max
python analyze_bottleneck.py   # 엔드포인트별 latency 분해 (병목 식별)
```

> **`--no-deps` 를 빼면** k6 의 `depends_on: gateway` 가 의존성 트리를 다시 띄우며 `--scale orderapi=$N` 을
> 기본값 1 로 되돌린다 — 측정을 통째로 무효화하는 함정이니 반드시 유지.

## 시드 구성 (자립)

이 시나리오의 `seed/` 는 **자립적**이다 — 부하 테스트에 필요한 모든 것을 스스로 만들고,
상시환경 공통셋([루트 seed/](../../seed/))이나 다른 시나리오에 의존하지 않는다:

| | `seed/user.sql` | `seed/order.sql` |
|---|---|---|
| 내용 | seller1(id=1) + customer 1000 (`customer{i}@qa.test`) | product 100 / item 500 (id 1..500, `seller_id=1`) |
| 규모 | 판매자 1 + 고객 1000 | 상품 100 / item 500, 재고 1M |
| cleanup | 자기 행만 (`seller id=1`, `customer<digits>`) | 자기 행만 (`QaProduct%`) |

- 주입 순서: **user.sql → order.sql** (order 의 `seller_id=1` 이 user.sql 의 seller 를 참조).
- qa 스택은 `name: qa-load-balancing` 으로 별도 DB 라, 자기 seller(id=1)를 만들어도 상시환경과 같은 DB 에 공존하지 않아 충돌이 없다.
- 이메일 검증 단계 우회: SQL 로 `verify=true` 직접 INSERT.

> 상시 테스트 환경(compose/k8s)의 공통 베이스 픽스처(seller 1·2, 엣지케이스 상품·고객)는
> 이 시나리오와 분리돼 **루트 [seed/](../../seed/)** 에 있다.

## 자동 시드 (db-seed)

스키마는 Flyway(앱 부팅 시 생성)라 MySQL `/docker-entrypoint-initdb.d/` 로는 못 넣는다(테이블 생성 전 실행). 그래서 **마이그레이션 후 테이블을 폴링하다 주입하는 1회용 러너**를 둔다:

- 이 시나리오의 `docker-compose.qa.yml` `db-seed` 서비스: 서비스 목록 없이 평범한 `up` 시 **시나리오 자립 시드**(`./seed`)를 자동 주입.
  - 위 수동 측정 절차는 `up` 에 서비스를 명시해 db-seed 를 제외하고 user → order 를 직접 주입한다 (주입 시점 제어).

> 상시환경(`docker-compose.test.yml`, `k8s/overlays/test`)의 db-seed 는 이 시나리오가 아니라
> **루트 [seed/](../../seed/)** 공통 베이스를 주입한다. k8s 는 트리 밖(`../../../seed/`) 참조라 적용 시:
>
> ```bash
> kustomize build k8s/overlays/test --load-restrictor LoadRestrictionsNone | kubectl apply -f -
> # 재적용 시 Job 은 불변 → kubectl delete job db-seed -n commerce 후 재적용
> ```

## 멱등성 검증

k6 워크로드는 5 % 확률로 **같은 Idempotency-Key 로 두 번 전송**.
- 정상: 두 번째 요청은 [ADR-001](../../ADR/001-idempotency-double-payment-prevention.md)
  의 IdempotencyService 가 캐시된 응답 반환 → DB 에 중복 Order 생성 0
- 위반: 두 번째 요청도 새 Order 생성 → `duplicate_order_responses` 카운터 증가

## 합격 기준 (ADR-005 §합격 기준 인용)

- E1 → E2 p99 감소율 ≥ 30 %
- E2 → E3 throughput 증가율 ≥ 70 %
- 각 인스턴스 요청 분배 편차 ±10 % 이내
- 에러율 < 0.5 %
- 멱등성 위반 = 0
- PENDING 으로 남은 Order = 0

> 실측 결과·분석은 [results/MEASUREMENT_REPORT.md](results/MEASUREMENT_REPORT.md) 참조
> (요약: cart→order 워크로드는 flat, order-only 에서 E1→E2 throughput +71 % 로 LB 효과 입증).

## 알려진 제약

- 시드 SQL 의 seller_id 는 1 로 고정 (user.sql 이 seller1=1 생성 → order.sql 이 참조. user.sql → order.sql 순서 보장)
- Redis 카트는 k6 가 매 반복마다 동적으로 추가 (시드 SQL 범위 밖)
- docker-compose `deploy.resources.limits` 는 Docker Desktop 에서 동작
  (Linux daemon 의 Swarm 모드와는 다르지만 단일 노드에서는 적용됨)
- 컨테이너명은 `docker-compose.qa.yml` 의 `name: qa-load-balancing` 으로 `qa-load-balancing-*` 고정 — `monitor-stats.sh` 의 `qa-load-balancing-mysql-order-1` 등 하드코딩이 이에 의존
