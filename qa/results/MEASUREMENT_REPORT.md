# ADR-005 시나리오 3 측정 결과

- 측정 일자: 2026-05-26
- 측정 환경: Docker Desktop 단일 노드, 16 CPU / 16 GB RAM 할당
- 인스턴스당 자원 한도: 2 CPU / 1 GB
- 워크로드: k6 ramping-vus 0 → 50 (30s warm-up) → 200 (1m ramp) → 200 (3m steady) → 0 (30s cool-down), 총 5 분
- 측정 대상: `POST /order/customer/cart/order` 응답 시간 (gateway → orderApi)
- 시드: 1000 customer (verify=true), 100 product × 5 product_item (재고 1M)

## 결과 요약

| 지표 | E1 (1) | E2 (2) | E3 (4) |
|---|---|---|---|
| 총 HTTP 요청 | 27,304 | 26,854 | 26,020 |
| iteration (= 주문 시도) | 9,004 | 9,033 | 8,740 |
| **throughput (req/s)** | **91.0** | **89.5** | **86.7** |
| order p50 (ms) | 21.2 | 18.3 | 17.7 |
| order p90 (ms) | 27.6 | 25.1 | 24.6 |
| order p95 (ms) | 30.4 | 27.7 | 28.6 |
| order max (ms) | 181 | 147 | **3,068** |
| order 성공률 (check) | 99.92 % | 96.90 % | 98.19 % |
| **멱등성 위반** | **0** | **8** | **3** |
| Idempotency-Key 재전송 시도 | 507 | 432 | 397 |
| PENDING 잔여 | 5,208 | 2,613 | 2,574 |
| FAILED | 3,789 | 5,718 | 5,623 |
| Outbox 미발행 잔여 | 0 | 0 | 0 |
| SAGA end-to-end avg (ms) | 247,348 | 197,193 | 199,214 |
| SAGA end-to-end max (ms) | 287,922 | 267,073 | 260,717 |

(SAGA 측정 모집단: order.status ∈ {CONFIRMED, FAILED} 만 포함, 즉 SAGA 가 완결된 주문만)

## 합격 기준 평가

| 조건 | 통과선 | 실측 | 결과 |
|---|---|---|---|
| E1 → E2 p99 감소율 | ≥ 30 % | p95 기준 -8.9 % (30.4 → 27.7 ms) | ❌ 미달 |
| E2 → E3 throughput 증가율 | ≥ 70 % | -3.1 % (89.5 → 86.7 req/s) | ❌ 미달 |
| 각 인스턴스 분배 편차 | ±10 % 이내 | 측정 불가 (k6 submetric tag 미수집) | ⚠️ 보고 |
| 에러율 (order check) | < 0.5 % | 0.08 % / 3.10 % / 1.81 % | ❌ E2/E3 미달 |
| **멱등성 위반 = 0** | **0** | **0 / 8 / 3** | ❌ **E2/E3 위반** |
| PENDING 잔여 = 0 | 0 | 5208 / 2613 / 2574 | ❌ 측정 종료 시점에 미완결 |

→ **합격 기준 6개 중 1개도 통과하지 못함.** ADR-005 의 "orderApi 다중화로 LB 효과가 정량적으로 드러난다" 라는 가설이 본 측정에서는 **실증되지 않았다.**

## 핵심 발견

### 발견 1. throughput 이 flat (91 → 89.5 → 86.7 req/s)

인스턴스를 4 배 늘렸음에도 처리량은 사실상 동일하거나 약간 감소. ADR-005 의 예상 (선형 ~4 배) 과 정반대 결과. 의미하는 바:

- **orderApi 가 병목이 아니다.** 단일 인스턴스도 ~90 req/s 부하를 여유 있게 처리 (p95 = 30 ms).
- 실제 병목은 다른 곳:
  - gateway (단일 인스턴스, 1 CPU / 768 MB)
  - MySQL 두 개 (커넥션 풀 · fsync)
  - Redis (단일, 카트 read/write 직렬화)
  - Kafka (단일 broker, 단일 partition?)
  - userApi (단일 인스턴스 — 결제 SAGA 단계의 bottleneck)

### 발견 2. 멱등성 위반 0 → 8 → 3 (E2/E3 에서 실패)

ADR-001 의 Redis SETNX 기반 IdempotencyService 가 다인스턴스 환경에서 동작 실패. 같은 `Idempotency-Key` 로 두 번 보냈을 때 서로 다른 응답 본문이 반환된 케이스가 E2 8 건, E3 3 건 관찰.

가설:
- 같은 Idempotency-Key 가 서로 다른 인스턴스로 라우팅되는 짧은 시점에 양쪽 SETNX 가 모두 성공 → 두 번 처리
- 또는 IdempotencyService.execute() 내부에서 cache write 와 SETNX 사이 race
- 또는 k6 의 detection 오탐 (응답 본문의 비결정적 필드 — 예: timestamp, orderId)

→ **이는 ADR-001 의 검증되지 않은 다인스턴스 결함**. 본 측정의 가장 큰 발견이며 별도 ADR-006 (분산 멱등성 강화) 후보.

### 발견 3. PENDING 잔여 — SAGA backpressure

각 실험 종료 + 60 초 안정화 후에도 PENDING 으로 남은 주문이 2,500 ~ 5,200 건. 이는:

- 측정 종료 시점에 SAGA 가 아직 진행 중인 주문 (정상)
- Outbox 잔여 = 0 이므로 ORDER_CREATED 이벤트는 모두 발행됨
- 즉 **userApi 의 PaymentConsumer + orderApi 의 StockConsumer 처리량이 발행량을 못 따라감**

SAGA end-to-end p99 가 **3 분 ~ 5 분** 에 달하는 것이 이 backpressure 의 직접 증거. 인스턴스 수와 무관 (E1: 247s avg, E2: 197s, E3: 199s) — orderApi 다중화는 SAGA 컨슈머 throughput 에 영향을 주지 못함 (각 컨슈머 그룹의 파티션 수 = 1 일 가능성, Kafka 단일 broker / topic 단일 partition 가정).

### 발견 4. E3 의 max latency 3068 ms (E1 의 17 배)

E1 max=181ms, E2 max=147ms 와 비교해 E3 의 max=3068ms 는 명백한 outlier. 가설:
- 4 인스턴스 부팅 + Eureka 등록 + gateway registry fetch 의 정착 지연
- 일부 요청이 ready 되지 않은 인스턴스로 라우팅되어 connection wait

### 발견 5. order 성공률이 다인스턴스에서 떨어짐

E1: 99.92 % → E2: 96.90 % → E3: 98.19 %. 정상 흐름이라면 다인스턴스에서 더 안정적이어야 하나 반대 추세.

가설:
- 같은 사용자가 빠르게 add-cart + order 반복하는 k6 패턴 + 다인스턴스에서 Redis 카트 read-modify-write race
- 같은 ProductItem 에 대한 낙관적 락 충돌 증가 (인스턴스가 늘어 동시 차감 시도가 늘어남)

## 측정 자체의 한계 (자가 비판)

- **k6 submetric tag 미수집** — `instance_hits{instance: ...}` 분포를 `--summary-export` JSON 에서 추출 못함. 분배 균등성 검증 불가. 후속: k6 `--out json` 으로 raw 이벤트 dump 후 별도 집계.
- **gateway / userApi 단일 인스턴스 고정** — orderApi 만 늘려도 결국 다른 구성요소가 천장. 측정 의도는 "LB 효과" 였으나 시스템 전체의 병목 위치를 드러내는 결과가 됨.
- **OutboxPoller 다인스턴스 중복 발행 미관측** — 컨슈머 idempotency 가 흡수하므로 표면화되지 않음. Kafka producer metrics 별도 수집 필요.
- **5 분은 짧다** — JVM warm-up + Eureka registry 정착 + cache hit ratio 정상화 등 정상 상태 도달 전 measurement 가 끝남.

## ADR-005 의 결정 갱신 제안

원안: "orderApi 다중 인스턴스 도입으로 트래픽 분산"

본 측정 결과에 따른 갱신:

1. **orderApi 다중화만으로는 throughput 개선 없음.** ADR-005 의 "scale-out 으로 p99 개선" 가설은 본 워크로드에서 **반증됨**.
2. **gateway · userApi · Kafka 의 단일 인스턴스가 실제 병목.** orderApi 다중화 이전에 이쪽들을 먼저 다중화해야 효과 측정 가능.
3. **ADR-001 (IdempotencyService) 의 다인스턴스 검증이 누락됐다.** 멱등성 위반 사례 발생 — 별도 ADR-006 으로 강화 필요.

## 후속 작업 (별도 ADR / 측정)

- **ADR-006 (제안)**: IdempotencyService 의 다인스턴스 race condition 분석 + Redis Lua 스크립트로 SETNX + 결과 캐싱 원자화
- **ADR-007 (제안)**: OutboxPoller 다인스턴스 중복 발행 방지 (`SELECT ... FOR UPDATE SKIP LOCKED` 또는 ShedLock)
- **별도 측정**: Kafka topic partition 수 증가 + userApi 다중화 + SAGA throughput 측정
- **별도 측정**: gateway 다중화 + L4 LB 효과
- **k6 보강**: `--out json` 으로 raw event 수집 → 인스턴스별 분배 정량 분석
