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
├── docker-compose.qa.yml        QA용 스택
├── build-images.sh              현재 소스로 이미지 빌드
├── run-consistency.sh           시나리오 1회 실행 + 집계
├── run-repeat.sh                시나리오 N회 반복 + 집계
├── chaos-schedule.sh            주변 장애 주입
├── quiescence-gate.sh           시나리오 종료 대기
├── verify-consistency.sh        정합성 검증
├── k6/load-test-consistency.js  통합 시나리오 스크립트
├── seed/                        시드 데이터
│   └── order.sql  
└── results/                     실행 산출물
```

## 사전 요구사항

- RAM 8GB / 8CPU 여유공간
- 빌드된 jar 파일
- WSL2 또는 Bash
- Docker (compose 환경)

## 실행

방어 상태는 **체크아웃한 브랜치**가 결정한다. 각 브랜치에서 같은 QA 를 돌려 두 측정을 얻는다.

```bash
# ── control(무방어) 측정 ──
git checkout v1  
cd qa/02-consistency
./build-images.sh
CHAOS=off ORDER_TARGET=2000 ARRIVAL_RATE=50 ./run-consistency.sh C-smoke # 2000건 스모크 테스트
ORDER_TARGET=100000 ./run-consistency.sh C-run1 # 실 테스트

# ── treatment(방어) 측정 ──
git checkout feature/66-consistency-integration-scenario
cd qa/02-consistency
./build-images.sh
CHAOS=off ORDER_TARGET=2000 ARRIVAL_RATE=50 ./run-consistency.sh T-smoke # 2000건 스모크 테스트
ORDER_TARGET=100000 ./run-consistency.sh T-run1 # 실 테스트

# ── 비교 (KPI: N → r) ──
diff <(sed -n '/집계 KPI/p' results/C-run1-verify.txt) <(sed -n '/집계 KPI/p' results/T-run1-verify.txt)
```

> **N회 반복 테스트**: `./run-repeat.sh N ~`

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
