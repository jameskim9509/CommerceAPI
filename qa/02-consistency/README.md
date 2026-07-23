# 시나리오 02 — 주문 정합성 통합 시나리오 (ADR-008)

> 상위 개요: [../README.md](../README.md) · 명세: [ADR-008](../../ADR/008-order-consistency-integration-scenario.md)

개별 정합성 메커니즘(ADR-001 멱등성 · ADR-002 재고 낙관적 락 · ADR-003 SAGA 보상 · ADR-004 Outbox ·
⑤ 잔액 낙관적 락 · ⑥ 이벤트 중복/역순 dedup)이 **한 부하 안에서 동시에 발화**할 때 최종 DB 상태가
정합함을, **무방어(control) vs 방어(treatment) 집계 KPI `N→r`** 와 **교차 DB 불변식**으로 입증한다.

> **측정치는 전부 `⟨측정전⟩`.** 이 폴더는 "실행 가능한 하네스"를 제공하며, 실측은
> [`run-kpi-matrix.sh`](run-kpi-matrix.sh) 를 실제로 돌린 뒤 [results/MEASUREMENT_REPORT.md](results/MEASUREMENT_REPORT.md) 에 채운다.

## 무엇을 발화·검증하는가

**원하는 장애 6종**(전용 방어를 구현했고 이 시나리오가 의도적으로 발화):
① 중복 주문/결제 · ② 재고 초과판매 · ③ 결제 후 재고 실패→환불 · ④ 잔액 부족→결제 실패 · ⑤ 잔액 동시성 Lost Update · ⑥ 이벤트 중복/역순 배달.

**주변 장애 T1–T4**(아직 방어 없음 — 넣으면 깨진다, 복원력 gap 노출):
T1 reserveStock↔마커 비원자 → 정상 CONFIRMED 오환불 · T2 에러핸들러/DLT 부재 → 이벤트 드롭 ·
T3 Redis 멱등키 유실 → 중복 주문 · T4 Kafka 로그 전소 → 이벤트 소실. (상세는 ADR-008 §주변 장애)

## 측정 모델 — 무방어 vs 방어 집계 (N → r)

6개 방어를 **전부 끈 control** 과 **전부 켠 treatment(현재)** 를 **같은 부하·같은 bounded 고정 카오스 스케줄**로
돌려 교차 DB 불변식 위반 총수를 비교한다.

- **control (무방어)** → desired 위반 대량 + 카오스 잔여 ≈ **N**
- **treatment (현재)** → desired 방어(→0) + 카오스 잔여 ≈ **r** (0 아님)
- **KPI = N → r** — "6개 정합성 방어 설계로 통합 부하에서 정합성 위반 N → 소수 r 감소".
  r>0 은 6개 방어 대상이 아닌 주변 장애 T1–T4 가 **양쪽 arm 에 똑같이** 남기 때문.

### 이건 "개별 귀속 없는 집계 주장"이다 (한계)

- **개별 귀속 불가** — "`@Version`이 초과판매를 막았다"는 못 하고 "6개 방어 설계가 위반을 N→r 줄였다"만 주장.
- **N 은 증폭됨** — ① 없으면 중복주문 → ②⑤ 경합 폭증. 근본원인 하나가 하류 위반 여럿을 낳는다.
- **①(멱등)은 9개 SQL 불변식에 안 잡힘** — k6 `duplicate_order_responses` 를 집계 N 에 함께 포함한다.
- (⑤ 잔액 락만 예외로 `f0a48d5~1` vs `f0a48d5` git 짝 실험을 단일변수 보조 증거로 붙일 수 있음.)

## 디렉토리 구조

```
qa/02-consistency/
├── docker-compose.qa.yml        treatment(방어 ON) 스택. name:consist · kafka 4파티션 · redis/kafka ephemeral
├── docker-compose.control.yml   control 오버레이 — 6종 방어 OFF (①③④⑥ env, ②⑤ :control 이미지)
├── build-images.sh              treatment 이미지 빌드 (gradle bootJar → compose build)
├── run-consistency.sh           단일 arm 1회: up→seed→k6+chaos→quiescence→verify→down
├── run-kpi-matrix.sh            N회 interleaved (treatment/control) → results/KPI-MATRIX.md (median·range)
├── chaos-schedule.sh            bounded T1–T4 를 부하 진행도(20/40/60/80%)에 앵커해 각 1회 주입
├── quiescence-gate.sh           정착 게이트 (outbox=0 ∧ PENDING/PAID=0 ∧ lag=0 이 K회 연속)
├── verify-consistency.sh        교차 DB 3대 불변식 / 9개 체크 판정 (+ ① k6 dup 집계)
├── k6/load-test-consistency.js  constant-arrival-rate 부하 (85/8/5/2 mix + 멱등 재전송 overlay)
├── seed/scenario/
│   ├── user.sql                 ctrich{1..300}(10M) + ctbroke{1..60}(500) — 고객마다 balance_history 기준행 필수
│   └── order.sql                hot SKU(id 10001, count 1000) + 정상 SKU 50×5(재고 충분)
├── control/
│   ├── build-control-images.sh  @Version 오버레이 적용→무방어 jar→:control 이미지→원본 복구(trap)
│   └── overlay/{ProductItem,Customer}.java   ②⑤ @Version 제거본 (빌드 중에만 덮어씀)
└── results/                     실행 산출물 (*-k6-summary.json, *-verify.txt, KPI-MATRIX.md)
```

## 사전 요구사항

- Docker daemon + 약 8GB / 8CPU (orderApi 4 + userApi + MySQL×2 + Kafka + Eureka + Gateway 동시 기동, 한 번에 하나의 스택)
- 호스트 gradle 빌드 (Dockerfile 이 prebuilt `build/libs/*.jar` 를 COPY — Java 17)
- Windows 는 Git Bash / WSL2 (스크립트가 `MSYS_NO_PATHCONV=1`)

## 실행

```bash
cd qa/02-consistency

# (스모크) 작게 무카오스로 하네스 동작 확인 — control 변형버그 pre-flight 에도 사용
./build-images.sh
CHAOS=off ORDER_TARGET=2000 ARRIVAL_RATE=50 ./run-consistency.sh treatment T-smoke
cat results/T-smoke-verify.txt        # N 이 0 근처면 하네스 정상

# (전체 KPI 매트릭스) 무방어 N → 방어 r, N회 interleaved
./run-kpi-matrix.sh 10                 # ORDER_TARGET/ARRIVAL_RATE/CHAOS 는 env 로 조절
cat results/KPI-MATRIX.md
```

단일 arm 만:

```bash
./build-images.sh
./control/build-control-images.sh                       # control arm 을 돌릴 때만
ORDER_TARGET=100000 ./run-consistency.sh treatment T-run1
ORDER_TARGET=100000 ./run-consistency.sh control   C-run1
```

## 통합 시나리오 구성 (기본 10만 건)

| 주문 유형 | 비율 | 발화 벡터 | 발화하는 원하는 장애 |
|---|---:|---|---|
| 일반 정상 | 85% | ctrich(잔액 충분) × 정상 SKU(재고 충분) | 해피패스 + ①⑥ + 아웃박스 |
| 한정 재고 경합 | 8% | ctrich × hot SKU(count 1000 ≪ 8k 수요) | ② 재고 락 경합/초과판매 · ③ 소진→환불 |
| 잔액 부족 | 5% | ctbroke(잔액 500) × 정상 SKU | ④ 결제 실패 분기 |
| 멱등 재전송 overlay | 2% | 정상 주문 + 같은 Idempotency-Key 재전송 | ① 중복 결제 |

**주입 조건**: orderApi 4인스턴스 × Kafka 4파티션(`KAFKA_CFG_NUM_PARTITIONS: 4`)로 같은 hot SKU/고객을 병렬
처리 → ②⑤⑥ 경합 발화. 작은 고객 풀(360명 ≪ 10만)로 "같은 고객 경합" → ⑤ 잔액 Lost Update 발화.

> ★ **시드 정합 규칙**: hot/normal SKU 의 이름·설명·가격은 k6 payload 와 정확히 일치해야 한다
> (`CartService.refreshCart` 가 DB 와 비교 → 다르면 `CART_CHECK_REQUIRED` 로 SAGA 진입 자체가 막힘). 전부 ASCII.
> ★ **잔액 시드 규칙**: 결제 가능액은 `customer.balance` 가 아니라 최신 `customer_balance_history.change_money`
> 로 판정된다 — 고객마다 기준 이력 행이 반드시 있어야 한다(없으면 시작 잔액 0 → 전건 결제 실패, ADR-005 시드 버그).

## 정착 → 판정

1. **부하 종료** — k6 가 요청을 멈춘다 (constant-arrival-rate, 고정 RPS).
2. **정착(quiescence) 게이트** — 고정 sleep 아님: 양 DB `outbox 미발행=0` ∧ `PENDING/PAID=0` ∧ `consumer lag=0`
   이 K회 연속일 때까지 폴링 ([quiescence-gate.sh](quiescence-gate.sh)).
3. **교차 DB 3대 불변식 / 9개 체크** ([verify-consistency.sh](verify-consistency.sh)) — 런 스코프는 시드 명명(`ctrich%`/`ctbroke%`, hot id 10001):

| 불변식 | 체크 | 판정 |
|---|---|---|
| ① 멱등성 | duplicate_order_responses (k6) | = 0 |
| ② 초과판매 | 음수 재고 · 차감량==CONFIRMED 수량 · CONFIRMED≤초기재고 | = 0 |
| ③ 결제·재고 정합성(돈 보존) | PENDING/PAID 잔여 · outbox 미발행(양 DB) · 돈 보존(Σ잔액감소==ΣCONFIRMED) · 음수 잔액 | = 0 |

> ⚠ **T4 거짓 PASS**: "outbox 미발행=0"은 `sent_at IS NULL` 만 세므로 kafka 로그 전소(T4) 시 이미 sent 표기라
> 손실을 놓친다 → T4 는 **돈 보존·PENDING/PAID 잔여**로만 잡히는 은닉 손실이다.

## control(무방어) 빌드 — 6종 전부 소스 오버레이

**운영 코드에는 방어를 끄는 스위치(토글/프로퍼티)가 일절 없다** — 실수로 prod 에서 방어가 꺼지는 footgun 을
원천 차단한다. control 은 `control/overlay/` 아래 "무방어본" 소스를 **빌드 중에만** 원본 위에 덮어써 만든
`:control` 이미지로, `docker-compose.control.yml` 은 orderapi/userapi 를 그 이미지로 **교체만** 한다(env 없음).

| 방어 | 제거 방식 | 오버레이 파일 (원본 = 이 파일 − 방어) |
|---|---|---|
| ① 멱등 게이트 | `execute()` 가 게이트 없이 매 요청 실행 | `orderApi/.../idempotency/IdempotencyService.java` |
| ② 재고 낙관적 락 | 엔티티 `@Version` 제거 | `orderApi/.../domain/ProductItem.java` |
| ③ 환불 보상 | `RefundConsumer` 가 이벤트만 소비, 환불 안 함 | `userApi/.../saga/consumer/RefundConsumer.java` |
| ④ 잔액 검증 | `NOT_ENOUGH_BALANCE` 검사 제거(음수 허용) | `userApi/.../service/customer/CustomerBalanceHistoryService.java` |
| ⑤ 잔액 낙관적 락 | 엔티티 `@Version` 제거 | `userApi/.../domain/customer/Customer.java` |
| ⑥ dedup | `processed_events` 검사 제거(+ userApi 는 락 재시도 제거) | `orderApi`·`userApi` `.../saga/IdempotentEventHandler.java` |

`control/build-control-images.sh` 가 오버레이(소스트리 미러)를 적용 → 무방어 jar 빌드 → `:control` 태깅 →
`trap` 으로 **원본을 항상 복구**(운영 소스 오염 없음). control 실행은 반드시 `--no-build`(안 그러면 현재 소스로
재빌드해 `:control` 을 오염). **주의**: 오버레이는 원본과 "방어만 다르게" 유지해야 한다 — 원본 서비스 코드를
크게 바꾸면 측정 전 오버레이를 갱신할 것(불일치 시 빌드가 컴파일 에러로 시끄럽게 실패한다).

## 알려진 제약 / 정직성

- 수치는 전부 `⟨측정전⟩` — 카오스 하네스·무방어 빌드는 이번에 구현했으나 ≥10회 100k 매트릭스는 아직 미실행.
- 집계 KPI 는 **개별 귀속 불가**(§한계). 단일 헤드라인 %로 세탁하지 않는다.
- 근본 수정(DLQ/정산 큐, 아웃박스 poison-row 격리 등)은 본 시나리오 범위 밖 — ADR-008 §후속 과제.
