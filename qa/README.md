# ADR-005 시나리오 3 — QA 측정 인프라

orderApi 인스턴스를 1 / 2 / 4 로 늘리면서 Gateway + Eureka LoadBalancer 의
부하 분산 효과를 정량 측정한다. [ADR-005](../ADR/005-eureka-gateway-load-balancing.md)
의 "시나리오 3" 측정 설계를 그대로 구현.

## 디렉토리 구조

```
qa/
├── docker-compose.qa.yml      측정 전용 compose (작은 자원 한도 + k6 runner + db-seed)
├── seed/
│   ├── functional/            시나리오/수동 테스트용 베이스 픽스처 (모든 환경 자동 주입)
│   │   ├── user.sql           seller 1·2 + customer rich/poor/zero (id 9001+)
│   │   └── order.sql          QA-% 상품 5 + item 8 (품절 엣지케이스 포함, id 9001+)
│   └── load/                  k6 부하 전용 (qa 스택에서 functional 위에 추가 주입)
│       ├── user.sql           1000 customer (customer{i}@qa.test, verify=true)
│       └── order.sql          100 product × 5 product_item (id 1..500, 재고 1M)
├── k6/
│   └── load-test.js           ramping-vus 0→200, 5 분 시나리오
├── results/                   실행 결과 (각 실험별 JSON / 서버측 메트릭)
└── run-experiments.sh         E1 / E2 / E3 자동 실행 오케스트레이션
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
./qa/run-experiments.sh

# 3. 결과
ls qa/results/
# - E1-summary.json, E1-k6-summary.json, E1-server-side.md, E1-docker-stats.txt
# - E2-..., E3-...
```

## 수동 단일 실험 (디버깅용)

```bash
# 1) 스택 기동 (예: 2 인스턴스)
docker compose -f qa/docker-compose.qa.yml up -d --scale orderapi=2

# 2) 시드 (스택 ready 후) — functional 베이스 → load 레이어 순서 (functional 이 seller 1·2 점유)
docker compose -f qa/docker-compose.qa.yml exec -T mysql-user \
    mysql -uroot -proot user < qa/seed/functional/user.sql
docker compose -f qa/docker-compose.qa.yml exec -T mysql-order \
    mysql -uroot -proot orders < qa/seed/functional/order.sql
docker compose -f qa/docker-compose.qa.yml exec -T mysql-user \
    mysql -uroot -proot user < qa/seed/load/user.sql
docker compose -f qa/docker-compose.qa.yml exec -T mysql-order \
    mysql -uroot -proot orders < qa/seed/load/order.sql

# 3) k6 부하 테스트
EXPERIMENT_LABEL=E2 docker compose -f qa/docker-compose.qa.yml run --rm k6 \
    run /scripts/load-test.js

# 4) 정리
docker compose -f qa/docker-compose.qa.yml down -v
```

## 시드 구성 (functional / load)

시드는 용도별로 두 묶음이며 **한 DB 에서 충돌 없이 공존**한다:

| | `seed/functional/` | `seed/load/` |
|---|---|---|
| 용도 | 시나리오·수동 테스트 (품절·잔액부족 등 엣지케이스) | k6 부하 |
| 규모 | seller 2 + customer 3, 상품 5 / item 8 | customer 1000, 상품 100 / item 500 |
| ID 범위 | 9001+ (seller 만 1·2) | product/item 1..500 |
| 비밀번호 | `password1!` | `password` |
| cleanup | 자기 행만 (`customer-%`, `seller1/2`, `QA-%`) | 자기 행만 (`customer<digits>`, `QaProduct%`) |
| 주입 | **모든 환경 자동** (db-seed) | qa 스택에서 functional 위에 추가 |

- 공존 규칙: functional 이 **seller id 1·2 를 점유**하고, load 의 `seller_id=1` 이 이를 재사용한다 → functional 이 항상 먼저 주입돼야 한다.
- 이메일 검증 단계 우회: SQL 로 `verify=true` 직접 INSERT

## 자동 시드 (db-seed)

스키마는 Flyway(앱 부팅 시 생성)라 MySQL `/docker-entrypoint-initdb.d/` 로는 못 넣는다(테이블 생성 전 실행). 그래서 **마이그레이션 후 테이블을 폴링하다 주입하는 1회용 러너**를 둔다 — functional 시드를 자동 주입:

- **docker compose** (`docker-compose.test.yml`, `qa/docker-compose.qa.yml`): `db-seed` 서비스. 평범한 `up` 시 자동 실행.
  - `run-experiments*.sh` 는 `up` 의 서비스 목록에서 db-seed 를 제외하고, functional → load 를 명시적으로 주입한다.
- **로컬 k8s** (`k8s/overlays/test`): `db-seed` Job (+ `qa/seed/functional/` 을 출처로 한 ConfigMap). 트리 밖 파일 참조라 적용 시:

  ```bash
  kustomize build k8s/overlays/test --load-restrictor LoadRestrictionsNone | kubectl apply -f -
  # 재적용 시 Job 은 불변 → kubectl delete job db-seed -n commerce 후 재적용
  ```

  → 테스트 데이터가 들어간 채로 시나리오를 바로 검증할 수 있다.

## 멱등성 검증

k6 워크로드는 5 % 확률로 **같은 Idempotency-Key 로 두 번 전송**.
- 정상: 두 번째 요청은 [ADR-001](../ADR/001-idempotency-double-payment-prevention.md)
  의 IdempotencyService 가 캐시된 응답 반환 → DB 에 중복 Order 생성 0
- 위반: 두 번째 요청도 새 Order 생성 → `duplicate_order_responses` 카운터 증가

## 합격 기준 (ADR-005 §합격 기준 인용)

- E1 → E2 p99 감소율 ≥ 30 %
- E2 → E3 throughput 증가율 ≥ 70 %
- 각 인스턴스 요청 분배 편차 ±10 % 이내
- 에러율 < 0.5 %
- 멱등성 위반 = 0
- PENDING 으로 남은 Order = 0

## 알려진 제약

- 시드 SQL 의 seller_id 는 1 로 고정 (functional 이 seller1=1 점유 → load 가 재사용. functional → load 순서 보장)
- Redis 카트는 k6 가 매 반복마다 동적으로 추가 (시드 SQL 범위 밖)
- docker-compose `deploy.resources.limits` 는 Docker Desktop 에서 동작
  (Linux daemon 의 Swarm 모드와는 다르지만 단일 노드에서는 적용됨)
