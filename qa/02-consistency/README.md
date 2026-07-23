# 시나리오 02 — 주문 정합성 통합 시나리오 (ADR-008)

> 상위 개요: [../README.md](../README.md) · 명세: [ADR-008](../../ADR/008-order-consistency-integration-scenario.md)

개별 정합성 메커니즘(ADR-001 멱등성 · ADR-002 재고 낙관적 락 · ADR-003 SAGA 보상 · ADR-004 Outbox ·
⑤ 잔액 낙관적 락 · ⑥ 이벤트 중복/역순 dedup)이 **한 부하 안에서 동시에 발화**할 때 최종 DB 상태가
정합함을, **무방어(control) vs 방어(treatment) 집계 KPI `N→r`** 와 **교차 DB 불변식**으로 입증한다.

> **측정치는 전부 `⟨측정전⟩`.** 이 폴더는 "실행 가능한 하네스"를 제공하며, 실측은
> 각 브랜치(무방어/방어)에서 QA 를 돌린 뒤 [results/MEASUREMENT_REPORT.md](results/MEASUREMENT_REPORT.md) 에 채운다.

## 무엇을 발화·검증하는가

**원하는 장애 6종**(전용 방어를 구현했고 이 시나리오가 의도적으로 발화):
① 중복 주문/결제 · ② 재고 초과판매 · ③ 결제 후 재고 실패→환불 · ④ 잔액 부족→결제 실패 · ⑤ 잔액 동시성 Lost Update · ⑥ 이벤트 중복/역순 배달.

**주변 장애 T1–T4**(아직 방어 없음 — 넣으면 깨진다, 복원력 gap 노출):
T1 reserveStock↔마커 비원자 → 정상 CONFIRMED 오환불 · T2 에러핸들러/DLT 부재 → 이벤트 드롭 ·
T3 Redis 멱등키 유실 → 중복 주문 · T4 Kafka 로그 전소 → 이벤트 소실. (상세는 ADR-008 §주변 장애)

## 측정 모델 — 무방어 vs 방어 집계 (N → r)

6개 방어를 **전부 끈 control** 과 **전부 켠 treatment(현재)** 를 **같은 부하·같은 bounded 고정 카오스 스케줄**로
돌려 교차 DB 불변식 위반 총수를 비교한다. 두 arm 은 **git 브랜치**로 나뉜다(§실행).

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
├── docker-compose.qa.yml        QA 스택. name:consist · kafka 4파티션 · redis/kafka ephemeral (방어 상태는 브랜치가 결정)
├── build-images.sh              현재 브랜치 소스로 이미지 빌드 (gradle bootJar → compose build)
├── run-consistency.sh           1회 실행: up→seed→k6+chaos→quiescence→verify→down (라벨로 arm 구분)
├── run-repeat.sh                현재 브랜치에서 N회 반복 + 집계 → results/<prefix>-AGGREGATE.md (median·range)
├── chaos-schedule.sh            bounded T1–T4 를 부하 진행도(20/40/60/80%)에 앵커해 각 1회 주입
├── quiescence-gate.sh           정착 게이트 (outbox=0 ∧ PENDING/PAID=0 ∧ lag=0 이 K회 연속)
├── verify-consistency.sh        교차 DB 3대 불변식 / 9개 체크 판정 (+ ① k6 dup 집계)
├── k6/load-test-consistency.js  constant-arrival-rate 부하 (85/8/5/2 mix + 멱등 재전송 overlay)
├── seed/scenario/
│   ├── user.sql                 ctrich{1..300}(10M) + ctbroke{1..60}(500) — 고객마다 balance_history 기준행 필수
│   └── order.sql                hot SKU(id 10001, count 1000) + 정상 SKU 50×5(재고 충분)
└── results/                     실행 산출물 (*-k6-summary.json, *-verify.txt, *-AGGREGATE.md)

# 방어 상태 = git 브랜치 (arm):
#   control(무방어) = 브랜치 control/no-defense (tag v1) = feature − 6개 방어 (QA 하네스 공유)
#   treatment(방어) = feature / main
# 운영 코드엔 방어-off 스위치가 없다(footgun 없음). 무방어 소스는 v1 브랜치에만 존재.
```

## 사전 요구사항

- Docker daemon + 약 8GB / 8CPU (orderApi 4 + userApi + MySQL×2 + Kafka + Eureka + Gateway 동시 기동, 한 번에 하나의 스택)
- 호스트 gradle 빌드 (Dockerfile 이 prebuilt `build/libs/*.jar` 를 COPY — Java 17)
- Windows 는 Git Bash / WSL2 (스크립트가 `MSYS_NO_PATHCONV=1`)

## 실행 — 브랜치 = arm

방어 상태는 **체크아웃한 브랜치**가 결정한다. 각 브랜치에서 같은 QA 를 돌려 두 측정을 얻는다.

```bash
# ── control(무방어) 측정: 무방어 브랜치에서 ──
git checkout v1                        # = control/no-defense (feature − 6방어, QA 공유)
cd qa/02-consistency
./build-images.sh                      # 현재 브랜치(무방어) 소스로 이미지 빌드
ORDER_TARGET=100000 ./run-consistency.sh C-run1     # → results/C-run1-verify.txt = 무방어 N

# ── treatment(방어) 측정: 방어 브랜치에서 ──
git checkout feature/66-consistency-integration-scenario
cd qa/02-consistency
./build-images.sh                      # 현재 브랜치(방어) 소스로 이미지 빌드
ORDER_TARGET=100000 ./run-consistency.sh T-run1     # → results/T-run1-verify.txt = 방어 r

# ── 비교 (KPI: N → r) ──
diff <(sed -n '/집계 KPI/p' results/C-run1-verify.txt) <(sed -n '/집계 KPI/p' results/T-run1-verify.txt)
```

- **스모크**(하네스 동작 확인): `CHAOS=off ORDER_TARGET=2000 ARRIVAL_RATE=50 ./run-consistency.sh smoke`
- **N회 반복**(≥10 권장): 각 브랜치에서 `./run-repeat.sh 10 C`(무방어) / `./run-repeat.sh 10 T`(방어) → `results/{C,T}-AGGREGATE.md` 중앙값 비교

> ⚠ **N=1 은 점추정**이다(ADR-008 §재현 하네스 4 는 ≥10회 권장). 리포트에 **"N=1 point estimate"** 로 명시할 것.
> ⚠ **무방어 브랜치를 방어 브랜치에 merge 하지 말 것** — 방어가 사라진다. main 으로 가져갈 건 측정 리포트(숫자)뿐.

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

## control(무방어) = 무방어 전용 브랜치 (arm)

**운영 코드(feature = treatment)에는 방어를 끄는 스위치가 일절 없다** — prod 에서 실수로 방어가 꺼지는
footgun 을 원천 차단한다. 무방어 소스는 **별도 git 브랜치 `control/no-defense`(tag `v1`) = feature − 6개 방어**
에만 존재하며, 그 브랜치를 체크아웃해 같은 QA 를 돌리면 control 측정이 된다(별도 control 이미지·오버레이·토글 없음).

| 방어 | 제거 (control/no-defense 브랜치, 최소 diff) |
|---|---|
| ① 멱등 게이트 | `IdempotencyService.execute()` 가 게이트 없이 매 요청 실행 |
| ② 재고 낙관적 락 | `ProductItem` 의 `@Version` 삭제 |
| ③ 환불 보상 | `RefundConsumer` 가 이벤트만 소비, 환불 안 함 |
| ④ 잔액 검증 | `CustomerBalanceHistoryService` 의 `NOT_ENOUGH_BALANCE` 검사 삭제 |
| ⑤ 잔액 낙관적 락 | `Customer` 의 `@Version` 삭제 |
| ⑥ dedup | `IdempotentEventHandler`(양 모듈)의 `processed_events` 검사 삭제 |

**왜 브랜치인가** — env 토글은 무방어 분기가 운영 이미지에 실려 footgun; 소스 오버레이는 원본이 바뀌면 조용히
어긋남(drift). 브랜치는 **footgun 0(운영 pristine)** + **drift 관리**(아래) + **@Version 자연 처리**(그냥 삭제)를 다 만족한다.

**드리프트 관리**: 한 번 측정이면 `control/no-defense` 를 feature 최신에서 뽑았으니 drift 0. 반복/재측정 시엔
`git switch control/no-defense && git merge feature` 로 상류 변경을 반영한다 — **충돌 지점이 곧 방어 제거 지점**이라,
오버레이의 "조용한 drift"와 달리 어긋남이 눈에 보인다.

### control/no-defense 브랜치 (재)생성 / 갱신

```bash
git switch -c control/no-defense feature/66-consistency-integration-scenario   # 최초 1회
# 6개 방어 최소 diff 제거: @Version(ProductItem·Customer) 삭제 + 멱등 게이트/dedup/환불/잔액검증 제거
git commit -am "chore(control): 무방어 빌드 — 6종 방어 제거"
git tag -a v1 -m "ADR-008 control 무방어 스냅샷"
git switch feature/66-consistency-integration-scenario
# 이후 상류 변경 반영: git switch control/no-defense && git merge feature (충돌=방어지점) && git tag -f v1 && git switch -
```

## 알려진 제약 / 정직성

- 수치는 전부 `⟨측정전⟩` — 하네스·무방어 브랜치는 구현했으나 ≥10회 100k 측정은 아직 미실행.
- 집계 KPI 는 **개별 귀속 불가**(§한계). 단일 헤드라인 %로 세탁하지 않는다.
- 근본 수정(DLQ/정산 큐, 아웃박스 poison-row 격리 등)은 본 시나리오 범위 밖 — ADR-008 §후속 과제.
