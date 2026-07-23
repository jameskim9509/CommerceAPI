# 시나리오 01 — 부하 분산 (ADR-005 시나리오 3)

> 상위 개요: [../README.md](../README.md) · 정합성 통합 시나리오(02-consistency)는 [ADR-008](../../ADR/008-order-consistency-integration-scenario.md) 명세대로 신규 작성 예정.

orderApi 인스턴스를 1 / 2 / 4 로 늘리면서 Gateway + Eureka LoadBalancer 의
부하 분산 효과를 정량 측정한다. [ADR-005](../../ADR/005-eureka-gateway-load-balancing.md)
의 "시나리오 3" 측정 설계를 그대로 구현.

## 디렉토리 구조

```
qa/01-load-balancing/
├── docker-compose.qa.yml           측정 전용 compose (작은 자원 한도 + k6 runner + db-seed). name: qa 고정
├── run-experiments.sh              E1 / E2 / E3 자동 실행 (cart→order 워크로드)
├── run-experiments-order-only.sh   E1o / E2o / E3o (order-only 워크로드 — cart_add throttle 제거)
├── monitor-stats.sh                부하 중 docker stats + MySQL + Eureka 를 5초 간격 캡쳐
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

## 실행

```bash
# 1. Docker daemon 확인
docker info

# 2. 자동 실행 (E1 → E2 → E3 순차)
./qa/01-load-balancing/run-experiments.sh

# 2b. order-only 변형 (E1o → E2o → E3o)
./qa/01-load-balancing/run-experiments-order-only.sh

# 3. 결과
ls qa/01-load-balancing/results/
# - E1-summary.json, E1-k6-summary.json, E1-server-side.md, E1-docker-stats.txt
# - E2-..., E3-...

# 4. 분석
python qa/01-load-balancing/parse_results.py
python qa/01-load-balancing/analyze_monitoring.py
```

## 수동 단일 실험 (디버깅용)

```bash
cd qa/01-load-balancing

# 1) 스택 기동 (예: 2 인스턴스)
docker compose -f docker-compose.qa.yml up -d --scale orderapi=2

# 2) 시드 (스택 ready 후) — user.sql(seller+customer) → order.sql(상품) 순서
docker compose -f docker-compose.qa.yml exec -T mysql-user \
    mysql -uroot -proot user < seed/user.sql
docker compose -f docker-compose.qa.yml exec -T mysql-order \
    mysql -uroot -proot orders < seed/order.sql

# 3) k6 부하 테스트 (★ --no-deps 필수: 없으면 --scale 이 1 로 리셋됨)
EXPERIMENT_LABEL=E2 docker compose -f docker-compose.qa.yml run --rm --no-deps k6 \
    run /scripts/load-test.js

# 4) 정리
docker compose -f docker-compose.qa.yml down -v
```

## 시드 구성 (자립)

이 시나리오의 `seed/` 는 **자립적**이다 — 부하 테스트에 필요한 모든 것을 스스로 만들고,
상시환경 공통셋([루트 seed/](../../seed/))이나 다른 시나리오에 의존하지 않는다:

| | `seed/user.sql` | `seed/order.sql` |
|---|---|---|
| 내용 | seller1(id=1) + customer 1000 (`customer{i}@qa.test`) | product 100 / item 500 (id 1..500, `seller_id=1`) |
| 규모 | 판매자 1 + 고객 1000 | 상품 100 / item 500, 재고 1M |
| cleanup | 자기 행만 (`seller id=1`, `customer<digits>`) | 자기 행만 (`QaProduct%`) |

- 주입 순서: **user.sql → order.sql** (order 의 `seller_id=1` 이 user.sql 의 seller 를 참조).
- qa 스택은 `name: qa` 로 별도 DB 라, 자기 seller(id=1)를 만들어도 상시환경과 같은 DB 에 공존하지 않아 충돌이 없다.
- 이메일 검증 단계 우회: SQL 로 `verify=true` 직접 INSERT.

> 상시 테스트 환경(compose/k8s)의 공통 베이스 픽스처(seller 1·2, 엣지케이스 상품·고객)는
> 이 시나리오와 분리돼 **루트 [seed/](../../seed/)** 에 있다.

## 자동 시드 (db-seed)

스키마는 Flyway(앱 부팅 시 생성)라 MySQL `/docker-entrypoint-initdb.d/` 로는 못 넣는다(테이블 생성 전 실행). 그래서 **마이그레이션 후 테이블을 폴링하다 주입하는 1회용 러너**를 둔다:

- 이 시나리오의 `docker-compose.qa.yml` `db-seed` 서비스: 평범한 `up` 시 **시나리오 자립 시드**(`./seed`)를 자동 주입.
  - `run-experiments*.sh` 는 `up` 의 서비스 목록에서 db-seed 를 제외하고 user → order 를 명시적으로 주입한다 (중복 방지).

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
- 컨테이너명은 `docker-compose.qa.yml` 의 `name: qa` 로 `qa-*` 고정 — `monitor-stats.sh` 의 `qa-mysql-order-1` 등 하드코딩이 이에 의존
