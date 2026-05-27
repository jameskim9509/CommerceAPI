# ADR-005 시나리오 3 측정 결과

- 측정 일자: 2026-05-26 ~ 2026-05-27
- 측정 환경: Docker Desktop 단일 노드, 16 CPU / 16 GB RAM 할당
- 인스턴스당 자원 한도: orderApi/userApi 2 CPU · 1 GB, gateway 1 CPU · 768 MB
- 워크로드: k6 ramping-vus 0 → 50 (30s warm-up) → 200 (1m ramp) → 200 (3m steady) → 0 (30s cool-down), 총 5 분
- 측정 대상: `POST /order/customer/cart/order` 응답 시간 (gateway → orderApi)
- 시드: 1000 customer (verify=true), 100 product × 5 product_item (재고 1M)
- 측정 회차: 모니터링 (docker stats + MySQL Threads_running) 포함 최종 회차 기준

## 결과 요약

| 지표 | E1 (1) | E2 (2) | E3 (4) |
|---|---|---|---|
| iteration (= 주문 시도) | 9,004 | 9,033 | 8,740 |
| **throughput (req/s)** | **91.0** | **89.5** | **86.7** |
| order p50 (ms) | 21.2 | 18.3 | 17.7 |
| order p90 (ms) | 27.6 | 25.1 | 24.6 |
| order p95 (ms) | 30.4 | 27.7 | 28.6 |
| order max (ms) | 181 | 147 | 3,068 |
| aggregate HTTP p95 (ms) | 6,663 | 6,587 | 6,580 |
| order 성공률 (check) | 99.92 % | 96.90 % | 98.19 % |
| 멱등성 위반 | 0 | 8 | 3 |

## 모니터링 측정값 (실측 데이터로 가설 검증)

5 초 간격으로 docker stats + MySQL `Threads_connected/running` 캡쳐.

### 컨테이너 CPU 사용률 (k6 부하 5 분 평균 / p95 / max, 단위: %)

| 컨테이너 | E1 avg/p95/max | E2 avg/p95/max | E3 avg/p95/max | 한도 |
|---|---|---|---|---|
| **userapi** | **194 / 216 / 219** | **201 / 212 / 216** | **185 / 211 / 211** | 2 코어 (200%) |
| qa-orderapi-1 | 53 / 101 / 119 | 46 / 82 / 88 | 46 / 95 / 101 | 2 코어 |
| qa-orderapi-2,3,4 | — | **모니터링 미캡쳐** | **모니터링 미캡쳐** | 2 코어 |
| kafka | 21 / 146 / 172 | 18 / 100 / 162 | 19 / 143 / 166 | unlimited |
| gateway | 18 / 54 / 54 | 18 / 40 / 46 | 17 / 46 / 53 | 1 코어 (100%) |
| mysql-order | 17 / 22 / 22 | 16 / 21 / 22 | 16 / 21 / 22 | unlimited |
| mysql-user | 6 / 11 / 26 | 6 / 8 / 10 | 6 / 9 / 26 | unlimited |
| eureka | 3 / 8 / 16 | 2 / 5 / 10 | 3 / 8 / 29 | unlimited |
| redis | 2 / 4 / 5 | 2 / 4 / 4 | 2 / 4 / 4 | unlimited |

### MySQL 상태 (mysql-order)

| 지표 | E1 | E2 | E3 |
|---|---|---|---|
| Threads_connected (avg/max) | **11 / 11** | **11 / 11** | **11 / 11** |
| Threads_running (avg/max) | 2.5 / 6 | 2.3 / 6 | 2.4 / 7 |
| queries/sec (5 분 window) | 707.8 | 699.9 | 639.9 |

## 핵심 발견

### 발견 1. **Gateway LB 가 분배 실패** — orderApi 다중화의 핵심 가정 무너짐

세 실험 모두 mysql-order 의 `Threads_connected = 11` 로 고정. HikariCP 기본 설정 (maximumPoolSize=10, minimumIdle=10) 으로 인스턴스 1 개당 ~10 connection 이 idle 로 유지돼야 함. 따라서:

| 실험 | 인스턴스 수 | 기대 connection | 실측 |
|---|---|---|---|
| E1 | 1 | ~10 | 11 ✓ |
| E2 | 2 | ~20 | **11** ✗ |
| E3 | 4 | ~40 | **11** ✗ |

→ **E2, E3 에서 orderapi-2, 3, 4 는 MySQL 에 connection 을 단 한 번도 열지 않음.** 즉 HikariCP lazy init 이 트리거되지 않을 만큼 HTTP 트래픽을 못 받음.

이는 docker stats 모니터링이 orderapi-1 만 캡쳐한 사실과도 일치 (orderapi-2/3/4 가 CPU 활성도 없어 모니터링 의미 없음 — 또는 모니터 스크립트가 idle 인스턴스를 스킵).

**ADR-005 의 핵심 가정**: "Gateway lb:// 가 Eureka 등록된 N 인스턴스에 round-robin 으로 분배"
**실측**: Gateway 가 orderapi-1 한 곳으로만 전송, 나머지 인스턴스는 사실상 idle.

가능성 높은 원인:
1. **Spring Cloud LB 의 `ServiceInstanceListSupplier` cache 갱신 늦음** — gateway 첫 fetch 시 orderapi-1 만 등록돼 있었을 가능성. 그 후 cache TTL 까지 stale.
2. **reactor-netty client 의 keep-alive connection affinity** — 한 번 맺은 connection 을 재사용해 다른 인스턴스를 시도하지 않음.
3. **Eureka registry propagation race** — gateway 가 fetch 한 시점에 orderapi-2/3/4 가 아직 등록 안 됨.

### 발견 2. **userApi CPU 가 진짜 병목** — 2 코어 한도에 saturate

세 실험 모두 userApi CPU 평균 184~201 %, p95/max 211~219 %. 2 코어 한도가 **사실상 항상 saturated**.

userApi 가 처리하는 작업 (SAGA 컨슈머 측):
- PaymentConsumer: ORDER_CREATED 수신 → Customer 잔액 조회/차감 → PaymentDeducted 발행
- RefundConsumer: STOCK_RESERVATION_FAILED 수신 → 환불 → PaymentReverted 발행
- OutboxPoller: 1 초마다 Kafka 발행

30 events/s × 4~5 DB 작업 × 단일 컨슈머 스레드. userApi 단일 인스턴스 2 코어로는 한도 도달.

이게 **SAGA end-to-end p99 가 3~5 분** 인 진짜 이유. 컨슈머가 발행 속도를 못 따라가 백로그 누적.

### 발견 3. **MySQL 은 병목이 아님** — 앞선 가설 무효

mysql-order CPU 평균 15~17 %, p95 ~22 %, Threads_running avg 2~3. 700 queries/sec 처리 중인데 자원 여유 충분.

이전 보고서 초안에서 "MySQL 단일 노드 천장" 으로 추정했으나, 모니터링 데이터로 **반증됨**. cart_add 의 aggregate p95 6.5 초도 MySQL 부하 때문이 아님.

### 발견 4. **cart_add p95 6.5 초의 진짜 원인** (가설)

orderApi 만 사용한 cart_add 가 6.5 초 걸리는데 mysql-order/Redis 모두 한가함. orderapi-1 CPU 도 평균 46~53 %.

가능성:
- **k6 의 200 VU 가 동시 cart_add 호출 → 모두 orderapi-1 으로 라우팅** → Tomcat 200 thread 한도 내 처리 가능하지만 HikariCP 풀 10 에 200 동시 요청 큐 형성
- **OSIV (Open Session In View) 가 HTTP 전체 구간 동안 connection 점유** → 큐 길이 증가
- 한 cart_add 가 ~30 ms 작업 + 큐 대기 ~6.5 초 = 관측치 6.5 초

⇒ ADR-005 의 LB 분배가 정상 동작했다면 4 인스턴스 × 풀 10 = 동시 40 처리 가능 → p95 1.5 초 미만으로 떨어졌어야 함. **LB 실패 (발견 1) 가 cart_add 병목의 직접 원인**.

### 발견 5. 멱등성 위반 0 / 8 / 3 — 다인스턴스에서만 발생

E1 = 0, E2 = 8, E3 = 3. 같은 Idempotency-Key 로 재시도 시 ADR-001 의 Redis SETNX 가 다인스턴스 환경에서 일부 race condition 노출. 정확한 메커니즘은 추가 조사 필요.

발견 1 과 결합해 보면: orderapi-1 만 사실상 동작했더라도 일부 요청은 orderapi-2/3/4 로도 갈 수 있음 — 그 짧은 분기 순간에 발생한 race 일 가능성.

## 합격 기준 평가

| 조건 | 통과선 | 실측 | 결과 |
|---|---|---|---|
| E1 → E2 p99 감소율 | ≥ 30 % | p95 -9 % (30.4 → 27.7) | ❌ |
| E2 → E3 throughput 증가율 | ≥ 70 % | -3 % | ❌ |
| 각 인스턴스 분배 편차 | ±10 % | 측정 시도, **분배 자체가 안 일어남** | ❌ |
| 에러율 (order check) | < 0.5 % | 0.08 / 3.10 / 1.81 % | ❌ |
| 멱등성 위반 | 0 | 0 / 8 / 3 | ❌ (E2/E3) |
| PENDING 잔여 | 0 | 수천 건 (SAGA backlog) | ❌ |

## 측정 자체의 한계 (자가 비판)

- **docker stats 가 orderapi-2/3/4 미캡쳐** — 모니터 스크립트가 idle 컨테이너를 스킵했거나 docker compose 의 scaled 인스턴스 이름 규칙 이슈. `docker ps --format` 을 매 sample 마다 재실행하므로 idle 인스턴스도 잡혀야 정상. Threads_connected = 11 의 정황 증거로 봐 LB 실패는 확실.
- **k6 submetric tag 미수집** — `instance_hits{instance:X}` 분포가 `--summary-export` 에 없어 인스턴스별 hits 직접 확인 불가.
- **gateway 라우팅 결정 로그 미수집** — `LoadBalancer choosing` 같은 디버그 로그가 있어야 분배 실패를 직접 확인 가능.

## ADR-005 의 결정 갱신 제안

원안 (Proposed): "orderApi 다중 인스턴스로 트래픽 분산 → throughput 선형 확장"

본 측정 결과:
1. **LB 자체가 분배에 실패함** — orderapi-2/3/4 는 idle. ADR-005 의 가장 핵심 가정이 본 환경에서 동작 안 함.
2. **userApi 가 SAGA 측 진짜 병목** — 2 코어 saturated.
3. orderApi 다중화는 (a) LB 가 안 되면 무의미, (b) 됐어도 userApi 가 천장이라 SAGA throughput 개선 한계.

**ADR-005 의 상태는 "제안 (Proposed)" 으로 유지하되, 본 측정 결과를 추가 섹션으로 첨부.** 본격 도입 전 다음 항목들을 먼저 해결 / 검증 필요.

## 후속 작업 (필수 선행 ADR / 측정)

### 즉시 (LB 동작 자체 검증)
- **gateway 디버그 로그 활성화**: `logging.level.org.springframework.cloud.loadbalancer=DEBUG` 로 분배 결정 추적
- **k6 의 X-Instance-Id 헤더 집계**: `--out json` 으로 raw event dump 후 인스턴스별 hits 계산 → LB 실패를 정량 증명
- **Spring Cloud LB cache 튜닝**: `spring.cloud.loadbalancer.cache.ttl=5s` 시도

### 단기 (1주)
- **ADR-006 후보**: gateway 의 `ReactorLoadBalancerExchangeFilterFunction` reuse 정책 + Eureka client `registry-fetch-interval-seconds` 단축 검토
- **ADR-007 후보**: IdempotencyService 다인스턴스 race 강화 (Redis Lua 스크립트 원자화) — 발견 5
- **ADR-008 후보**: OutboxPoller 다인스턴스 중복 발행 방지 (`SELECT ... FOR UPDATE SKIP LOCKED`)

### 중기 (별도 측정)
- **userApi 다중화 + Kafka partition 수 증가** 후 SAGA throughput 재측정 — 발견 2 해소 여부
- **cart_add 의 OSIV 끄기 (`spring.jpa.open-in-view=false`) 후 재측정** — 발견 4 검증
- **gateway 다중화 측정** (gateway 자체가 단일 노드라 ADR-005 도입 후에도 새 단일 점)
