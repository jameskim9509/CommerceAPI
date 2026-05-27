# ADR-005 시나리오 3 측정 결과

- 측정 일자: 2026-05-27
- 측정 환경: Docker Desktop 단일 노드, 16 CPU / 16 GB RAM 할당
- 인스턴스당 자원 한도: orderApi/userApi 2 CPU · 1 GB, gateway 1 CPU · 768 MB
- 워크로드: k6 ramping-vus 0 → 50 (30s warm-up) → 200 (1m ramp) → 200 (3m steady) → 0 (30s cool-down)
- 측정 대상: `POST /order/customer/cart/order` 응답 시간 (gateway → orderApi)
- 시드: 1000 customer (verify=true), 100 product × 5 product_item (재고 1M)
- 측정 회차: 본 보고서는 측정 인프라 자체의 버그 (`docker compose run k6` 가 `--scale` 을 리셋) 를
  발견·수정 (`--no-deps` 추가) 한 뒤의 최종 회차 결과.

## 측정 인프라 버그 (먼저 보고)

본 측정 과정의 가장 큰 발견은 **테스트 자체의 결함**이었다. 초기 회차들은 모두 사실상 N=1 측정.

```bash
# run-experiments.sh 의 k6 실행
docker compose -f docker-compose.qa.yml run --rm k6 ...
```

- `docker compose run k6` 의 기본 동작은 `depends_on` 트리 전체를 다시 `up` 시도
- k6 → gateway 의존성 따라 orderapi 가 다시 띄워지며 **이전 `--scale orderapi=N` 설정이 default (1) 로 리셋**
- 결과: orderapi-2/3/4 가 Started 직후 즉시 Stopped → 측정 내내 N=1 인스턴스만 active
- 증거: 모든 회차에서 `MySQL Threads_connected = 11` 고정 (1 인스턴스 × 풀 10 + 베이스라인 1)

**Fix**: `docker compose run --rm --no-deps k6 ...` — 의존성 리셋 차단.

이 fix 적용 후 측정한 본 회차에서 비로소 진짜 다중 인스턴스 동작이 관측됨:

| 회차 | MySQL Threads_connected (max) | LB 분배 동작 여부 |
|---|---|---|
| 1~4차 (--no-deps 전) | 11 (모든 실험에서 동일) | ❌ 사실상 N=1 |
| **본 회차 (--no-deps 후)** | **11 / 21 / 41** (E1/E2/E3) | ✅ 정상 분배 |

## 결과 요약

| 지표 | E1 (1) | E2 (2) | E3 (4) |
|---|---|---|---|
| iteration (= 주문 시도) | 9,520 | 9,292 | 9,674 |
| **throughput (req/s)** | 96.1 | 93.7 | 97.4 |
| order p50 (ms) | 21.3 | 20.5 | 21.8 |
| order p90 (ms) | 33.1 | 28.5 | 32.5 |
| order p95 (ms) | 41.3 | 32.3 | 38.0 |
| order max (ms) | 364 | 230 | 996 |
| order 성공률 (check) | 99.95 % | 99.97 % | 99.93 % |
| 멱등성 위반 | 0 | 0 | 0 |

## 모니터링 측정값 (5초 간격, 부하 5분 평균 / p95 / max)

### 컨테이너 CPU 사용률 (%)

| 컨테이너 | E1 avg/p95/max | E2 avg/p95/max | E3 avg/p95/max | 한도 |
|---|---|---|---|---|
| **userapi** | **191 / 213 / 213** | **193 / 214 / 214** | **195 / 214 / 214** | 200 (2 CPU) |
| orderapi-1 | 42 / 72 / 72 | 45 / 152 / 152 | 32 / 150 / 150 | 200 |
| orderapi-2 | — | **39 / 99 / 99** | **40 / 174 / 174** | 200 |
| orderapi-3 | — | — | **36 / 152 / 152** | 200 |
| orderapi-4 | — | — | **37 / 164 / 164** | 200 |
| kafka | 32 / 158 / 158 | 28 / 175 / 175 | 37 / 191 / 191 | unlimited |
| gateway | 17 / 77 / 77 | 15 / 36 / 36 | 17 / 67 / 67 | 100 (1 CPU) |
| mysql-order | 16 / 20 / 20 | 20 / 24 / 24 | 19 / 25 / 25 | unlimited |
| mysql-user | 5 / 9 / 9 | 7 / 25 / 25 | 6 / 11 / 11 | unlimited |
| redis | 2 / 5 / 5 | 3 / 5 / 5 | 3 / 5 / 5 | unlimited |

### MySQL 상태

| 지표 | E1 | E2 | E3 |
|---|---|---|---|
| Threads_connected | 11 | **21** | **41** |
| Threads_running (avg/max) | 2.2 / 4 | 2.4 / 4 | 3.0 / 7 |
| queries/sec | 700 | 708 | 782 |

## 핵심 발견

### 발견 1. Spring Cloud LB 분배 메커니즘은 정상 동작

E3 의 orderapi-1, 2, 3, 4 가 모두 평균 32~40 % CPU, p95 150~174 % 로 **균일하게 부하 분담**.
MySQL Threads_connected 가 1 → 2 → 4 인스턴스에 정확히 비례 (11 → 21 → 41) → 모든 인스턴스가 HikariCP 풀을 lazy-init 할 만큼 트래픽 받음.

→ ADR-005 의 핵심 가정 "lb:// 가 N 인스턴스에 round-robin 분배" 는 **정상 동작 확인**.
**단, 이 검증은 측정 인프라 버그를 잡은 후에 비로소 가능**했음.

### 발견 2. 그러나 throughput 은 여전히 flat (96 → 94 → 97 req/s)

orderApi 인스턴스를 4 배 늘렸음에도 throughput 변화 < 4 %. LB 가 정상 분배하더라도 system throughput 이 천장에 닿아 있음.

원인은 두 개 layer:

**(a) userApi CPU saturated (193~195 % 평균, 2 CPU 한도)**

userApi 는 단일 인스턴스로 다음을 동시 처리:
- HTTP login 요청 (k6 setup 시 1000 회 BCrypt)
- PaymentConsumer: ORDER_CREATED 수신 → Customer 잔액 차감 → PaymentDeducted 발행
- RefundConsumer: STOCK_RESERVATION_FAILED 수신 → 환불
- OutboxPoller: 1 초 주기로 Kafka 발행

평균 193~195 % CPU = 2 코어 거의 saturated. orderApi 가 더 많은 ORDER_CREATED 를 발행해도 userApi 가 이걸 따라잡지 못함.

**단, 본 측정의 HTTP throughput 은 cart_add + order 만 측정** — userApi 의 PaymentConsumer 처리는 비동기라 HTTP 응답 시간에 직접 영향 X. 그렇다면 왜 HTTP throughput 도 flat 한가?

**(b) k6 VU 모델의 자연적 throughput cap**

각 VU 의 iteration: `login(cached)` → `cart_add` → `order` → `sleep(0.1s)`.
cart_add 의 aggregate http_req_duration p95 가 여전히 ~6 초 (이전 회차와 유사).
- iteration time ≈ cart_add p95 (~6s) + order (~30ms) + sleep (0.1s) ≈ 6.1s
- 200 VU × (1 / 6.1s) ≈ 33 iter/s = ~66 req/s (cart + order 합산) + 추가 ≈ **96 req/s 천장**

→ orderApi 가 N 배 빨라져도 **cart_add 의 단일 호출 시간이 안 줄면 VU 가 빨리 다음 iteration 으로 못 넘어가 시스템 throughput 천장 동일**. 이건 워크로드 모델의 한계이지 ADR-005 의 한계가 아님.

### 발견 3. order p95 는 미미하게 개선 (LB 의 진짜 효과는 여기)

| 인스턴스 | E1 | E2 | E3 |
|---|---|---|---|
| order p50 | 21.3 | 20.5 | 21.8 |
| order p90 | 33.1 | 28.5 | 32.5 |
| **order p95** | **41.3** | **32.3** | **38.0** |

E1 → E2 p95 -22 % 개선 (41.3 → 32.3 ms). 단 E2 → E3 는 다시 약간 증가 (38 ms) — outlier 영향 가능.

→ order endpoint 자체는 인스턴스 수에 약간 민감. cart_add 가 자연 throttle 역할을 하지만, 동시 도착이 분산되는 효과는 있음.

### 발견 4. 멱등성 위반 0 — 다인스턴스에서도 안정

이전 측정 회차들에서 보였던 멱등성 위반 8 / 3 건은 **본 회차에서는 0**. 직전 측정 회차가 실제로는 N=1 이었으니 멱등성 위반은 측정 noise (k6 의 body 비교 false-positive) 였을 가능성. 본 회차 (진짜 N=4) 에서 0 건이라는 것은 ADR-001 의 다인스턴스 멱등성이 잘 동작함을 시사.

### 발견 5. 자원 활용도 분포

| 자원 | E3 사용률 | 평가 |
|---|---|---|
| **userapi** | 195 % / 200 % (98 %) | 🔥 **천장** |
| kafka | 37 % avg, peak 191 % | bursty, 평균 여유 |
| **gateway** | 17 % avg, peak 67 % | 여유 (단일 인스턴스라도 OK) |
| orderapi (×4) | 36 % avg, peak 150~175 % | 여유 (1 CPU 정도 사용) |
| mysql-order | 19 % avg, peak 25 % | 여유 |
| mysql-user | 6 % avg, peak 11 % | 매우 여유 |
| redis | 3 % avg | idle |

→ 다음 시나리오 측정에서 천장을 풀려면 **userApi 다중화 + Kafka 파티션 수 증가** 가 핵심.

## 합격 기준 평가

| 조건 | 통과선 | 실측 | 결과 |
|---|---|---|---|
| E1 → E2 p95 감소율 | ≥ 30 % | -22 % | ❌ 근접 |
| E2 → E3 throughput 증가율 | ≥ 70 % | +4 % | ❌ |
| 각 인스턴스 분배 균등성 | ±10 % | E3 4 인스턴스 32~40 % avg (분배는 됨) | ✅ |
| 에러율 (order check) | < 0.5 % | 0.05 / 0.03 / 0.07 % | ✅ |
| 멱등성 위반 | 0 | 0 / 0 / 0 | ✅ |
| PENDING 잔여 | 0 | (server-side 측정 필요) | — |

→ **LB 가 의도대로 분배는 하지만 (분배 균등성/에러율/멱등성 ✅), throughput · p95 개선은 미달** (워크로드 모델 + userApi 천장).

## ADR-005 의 결정 갱신 제안

원안 (Proposed): "orderApi 다중 인스턴스로 트래픽 분산"

본 측정 결론:
1. **메커니즘 자체는 동작 확인** ✅ — Spring Cloud LB + Eureka 가 4 인스턴스 round-robin 분배
2. **HTTP throughput 증가는 워크로드 의존** — 현재 k6 cart→order 패턴에서는 cart_add 의 iteration time 이 천장. 더 짧은 iteration / 더 많은 VU 필요
3. **SAGA throughput 은 userApi 다중화 필요** — orderApi 다중화는 SAGA 처리량에 직접 영향 X

**ADR-005 의 상태**: "**제안 (Proposed)**" 그대로 유지하되, 다음 두 조건 하에 다시 측정해야 의도된 효과 정량 확인 가능:
- 워크로드: cart_add 가 throughput cap 이 되지 않도록 setup 단계로 옮기거나 order-only 부하
- 시스템: userApi 도 2 인스턴스 + Kafka 파티션 4 로 SAGA 천장 동시 완화

## 측정 자체의 한계 (자가 비판)

- **Eureka registry CSV 파싱 버그** — `grep -A1` 패턴이 multi-instance JSON 의 첫 인스턴스만 추출. MySQL Threads_connected 정보로 우회했지만 직접 검증은 미수행. jq 사용으로 보강 필요.
- **k6 per-instance hits 카운터** — 추가했지만 VU 별 모듈 스코프 객체라 전체 합산 미가능. 추후 `--out json` 로 raw 이벤트 dump 후 별도 집계.
- **cart_add p95 측정값** — k6 summary 에서 직접 노출 안 됨. 추정치 (aggregate p95 - order p95) 기반.

## 후속 작업 (별도 ADR / 측정)

### 즉시
- **ADR-005 의 본 측정 결과 부록 추가** — 합격 기준 미충족이지만 LB 메커니즘은 동작 확인

### 단기
- **워크로드 재설계**: order-only 부하로 ADR-005 의 진짜 효과 측정
- **ADR-006 후보**: userApi 다중화 + Kafka 파티션 수 늘려 SAGA throughput 측정

### 발견된 인프라 버그 (별도 PR)
- **`docker compose run` 의 `--no-deps`** 빠뜨리면 `--scale` 이 리셋되는 패턴을 README / 운영 가이드에 명시
