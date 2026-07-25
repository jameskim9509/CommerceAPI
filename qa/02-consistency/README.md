# 주문 정합성 통합 시나리오 (ADR-008)

> 상위 개요: [../README.md](../README.md) · 명세: [ADR-008](../../ADR/008-order-consistency-integration-scenario.md)

정합성관련 상황(중복결제 · 초과판매 · 환불 · 동시 다중 주문시 잔액 Lost Update · 이벤트 중복/역순)이 **한 부하 안에서 동시에 발화**할 때 최종 DB 상태가 정합함(**집계 KPI `N→r`**)을 입증한다.

> 이 디렉토리는 "실행 가능한 하네스"를 제공하며, 실측은 각 브랜치(무방어/방어)에서 QA 를 돌린 뒤 [results/MEASUREMENT_REPORT.md](results/MEASUREMENT_REPORT.md) 에 채운다.

## 무엇을 발화·검증하는가

**의도된 장애**:

1. 중복 주문/결제
2. 재고 초과판매
3. 재고 소진→환불
4. 동시 다중 주문시 잔액 Lost Update
5. 이벤트 중복/역순 배달

**주변 장애**:

1. 주문 CONFIRMED 커밋과 이벤트 처리 이력 커밋 사이에 장애발생 → 공짜 주문
2. 에러 발생시 10회 재시도 후 이벤트 드롭
3. Redis 멱등키 유실시 중복 주문 허용
4. Kafka 로그 전소시 이벤트 소실

## 측정 모델 — 무방어 vs 방어 집계

- **control (무방어)** → 의도된 정합성 오류 + 카오스 잔여 ≈ **N**
- **treatment (방어)** → 의도된 정합성 오류(=0) + 카오스 잔여 ≈ **r**
- **KPI = N → r** — "5개 정합성 방어 설계로 통합 부하에서 정합성 위반 N → r".

## 디렉토리 구조

```
qa/02-consistency/
├── docker-compose.qa.yml        QA용 스택 (orderapi ×ORDERAPI_REPLICAS, kafka 4파티션)
├── build-images.sh              현재 브랜치 소스로 이미지 빌드
├── chaos-schedule.sh            주변 장애 주입 (부하와 동시에 백그라운드 실행)
├── quiescence-gate.sh           정착 대기 (비동기 흐름 종료 확인)
├── verify-consistency.sh        정합성 검증 (run 1건 판정)
├── aggregate-runs.sh            run 결과 집계 (원값·중앙값·범위)
├── k6/load-test-consistency.js  통합 시나리오 스크립트
├── seed/                        시드 데이터
│   ├── user.sql
│   └── order.sql
└── results/                     실행 산출물
```

## 사전 요구사항

- RAM 8GB / 8CPU 여유공간
- 빌드된 jar 파일
- WSL2 또는 Bash
- Docker (compose 환경)

## 실행 — 수동 측정

방어 상태는 **체크아웃한 브랜치**가 결정한다 (control 오버레이·이미지·토글 없음).
무방어 브랜치와 방어 브랜치에서 **같은 절차를 그대로** 돌려 두 측정을 얻는다.

```bash
# ── arm 선택: control(무방어) 은 v1, treatment(방어) 는 방어 브랜치 ──
git checkout v1                                  # treatment: feature/66-consistency-integration-scenario
cd qa/02-consistency
./build-images.sh                                # 브랜치를 바꿨으면 반드시 재빌드
```

한 run 은 아래 1)~8) 이다. 라벨만 바꿔가며 반복한다 (control `C-run1`, `C-run2` … / treatment `T-run1` …).

```bash
LABEL=C-run1                    # 체크아웃한 브랜치가 arm 을 결정한다 (treatment 면 T-run1)
export ORDERAPI_REPLICAS=4      # compose deploy.replicas 로 주입 — 세션 내내 export 유지

# 1) 이전 잔재 정리
docker compose -f docker-compose.qa.yml down -v --remove-orphans

# 2) QA 스택 기동 (k6 제외, orderapi ×4 × kafka 4파티션)
docker compose -f docker-compose.qa.yml up -d \
    mysql-user mysql-order redis kafka eureka userapi orderapi gateway db-seed

# 3) 시드 + 레지스트리 전파 대기
docker compose -f docker-compose.qa.yml wait db-seed
sleep 60                                                # Eureka 등록 → gateway fetch 전파 여유 (db-seed 는 미보장)

# 4) 카오스 백그라운드 — 주변 장애 T1~T4 를 부하 진행도에 앵커해 주입
ORDER_TARGET=100000 bash chaos-schedule.sh > results/$LABEL-chaos.log 2>&1 &
CHAOS_PID=$!                                            # 진행 확인: tail -f results/$LABEL-chaos.log

# 5) k6 셸 진입
RUN_LABEL=$LABEL ORDER_TARGET=100000 ARRIVAL_RATE=200 \
    docker compose -f docker-compose.qa.yml run --rm --entrypoint sh k6

# 6) k6 시나리오 실행 및 종료
k6 run /scripts/load-test-consistency.js
exit

# 7) 카오스 종료 → 정착 대기 → 검증
kill $CHAOS_PID 2>/dev/null                             # 미도달 앵커는 생략
bash quiescence-gate.sh                                 # outbox·PENDING/PAID·consumer lag 이 멎을 때까지
RUN_LABEL=$LABEL bash verify-consistency.sh             # → results/$LABEL-verify.txt

# 8) 정리 후 다음 run 으로
docker compose -f docker-compose.qa.yml down -v --remove-orphans
```

**스모크 (브랜치를 바꿀 때마다 1회)** — 변형 버그가 위반으로 오계수되지 않게, 카오스 없는 작은
clean 부하로 먼저 잔여 0 을 확인한다. 1)~3)·5)~8) 은 그대로 두고 **4)(카오스) 만 생략**한 뒤
라벨·부하 파라미터를 줄인다:

```bash
LABEL=C-smoke                   # treatment 는 T-smoke
# 4) 실행하지 않음 (= 무카오스 baseline)
# 5) 는 파라미터만 축소:
RUN_LABEL=$LABEL ORDER_TARGET=2000 ARRIVAL_RATE=50 \
    docker compose -f docker-compose.qa.yml run --rm --entrypoint sh k6
# 7) kill 은 생략, quiescence-gate → verify-consistency 는 동일 → results/$LABEL-verify.txt 가 N=0 이어야 한다
```

**반복·집계** — 점추정 금지(ADR-008 §재현 하네스 4, 권장 ≥10회). arm 마다 run 을 쌓은 뒤 한 번 집계한다.

```bash
./aggregate-runs.sh C           # → results/C-AGGREGATE.md (원값·중앙값·범위)
./aggregate-runs.sh T           # 방어 브랜치에서
```

**비교 (KPI: N → r)** — 두 AGGREGATE 의 중앙값을 비교한다. run 1건끼리 보려면:

```bash
diff <(sed -n '/집계 KPI/p' results/C-run1-verify.txt) <(sed -n '/집계 KPI/p' results/T-run1-verify.txt)
```

> **`ORDERAPI_REPLICAS` 는 세션 내내 유지** — 새 셸에서 k6 스텝만 다시 돌리면 변수가 없어도 기본 4 로
> 뜨지만, 다른 값으로 측정 중이었다면 조용히 4 로 되돌아간다.

## 통합 시나리오 구성 (기본 10만 건)

| 주문 유형        | 비율 | 주입 조건              | 원하는 장애                                     |
| ---------------- | ---: | ---------------------- | ----------------------------------------------- |
| 일반 정상        |  90% | 잔액 충분 × 재고 충분 | - 중복 주문/결제<br />- 이벤트 중복/역순 배달 |
| 한정 재고 경합   |   8% | 잔액 충분 × 재고 부족 | - 재고 초과판매<br />- 재고 소진→환불         |
| 동일 주문 재전송 |   2% | 주문 재전송            | - 중복 주문/결제                                |

**추가 주입 조건**

- orderApi 4인스턴스 × Kafka 4파티션으로 같은 주문을 병렬처리 → 2,4,5번 장애 발화.
- 같은 고객의 동시 다중 주문 경합 → 4번 장애 발화.

> **시드 정합 규칙**: 시드 재품의 이름·설명·가격은 k6 payload 와 정확히 일치해야 한다
> **잔액 시드 규칙**: 결제 가능액은 최신 `customer_balance_history`로 판정된다 — 고객마다 기준 이력 행이 반드시 있어야 한다.

## 판정

1. **K6 테스트 종료**
2. 타임아웃만큼 대기
3. DB 불변식 위반 개수 체크 (① + ② + ③)

| 불변식               | 위반 개수 체크                                                                             |
| -------------------- | ------------------------------------------------------------------------------------------ |
| ① 멱등성            | duplicate_order_responses 건수 (k6)                                                        |
| ② 초과판매          | (count<0 행 수) +\|초기재고−현재재고−CONFIRMED 수량\| + max(0, CONFIRMED 수량−초기재고) |
| ③ 결제·재고 정합성 | (PENDING/PAID 행 수) + (outbox 미발행 행 수, 양 DB) + (음수 잔액 행 수)                    |

> **돈 누수** = (초기잔액합 − 현재잔액합) − Σ CONFIRMED total_price
