# ADR-008 정합성 통합 시나리오 — 측정 리포트

> **상태: `⟨측정전⟩`** — 하네스는 구현·실행 가능하나 아직 측정을 돌리지 않았다.
> 무방어 브랜치(`v1`)와 방어 브랜치에서 각각 QA 를 돌린 뒤(README §실행) 아래 표를 채운다.
> 점추정 금지: 전 run 원값·중앙값·범위를 공표한다(N=1 이면 "point estimate" 명시).

## 실행 파라미터 (기록용)

| 항목 | 값 |
|---|---|
| ORDER_TARGET | `⟨측정전⟩` (명세 100,000) |
| ARRIVAL_RATE | `⟨측정전⟩` (기본 200/s, constant-arrival-rate) |
| orderApi 인스턴스 × Kafka 파티션 | `⟨측정전⟩` (명세 4 × 4) |
| 고객 풀 | ctrich 300 |
| hot SKU 초기재고 | 1,000 |
| 카오스 스케줄 | T2@20% · T3@40% · T1@60% · T4@80% (bounded, 양 arm 동일) |
| 반복 N | `⟨측정전⟩` (명세 ≥10 interleaved) |

## 집계 KPI — 무방어(N) → 방어(r)

| run | treatment r | control N | t money_leak(원) | c money_leak(원) | VOID? |
|----:|------------:|----------:|-----------------:|-----------------:|:-----:|
| 1 | `⟨측정전⟩` | `⟨측정전⟩` | `⟨측정전⟩` | `⟨측정전⟩` | |
| … | | | | | |

**요약(중앙값·범위):**

- treatment r: median `⟨측정전⟩`, range `⟨측정전⟩`
- control   N: median `⟨측정전⟩`, range `⟨측정전⟩`
- **KPI: N⟨측정전⟩ → r⟨측정전⟩**  (개별 귀속 없는 집계 주장)

> 예시 수치(`1000/13` 등)는 문서에 절대 넣지 않는다 — 실측 전까지 전부 `⟨측정전⟩`.

## 9개 체크별 잔여 (treatment, 대표 run)

| 체크 | 값 |
|---|---|
| ① duplicate_order_responses | `⟨측정전⟩` |
| ② 음수 재고 / 차감량−CONFIRMED / 초과 CONFIRMED | `⟨측정전⟩` |
| ③ PENDING·PAID 잔여 | `⟨측정전⟩` |
| ③ outbox 미발행 (orders / user) | `⟨측정전⟩` (T4 거짓 PASS 주의) |
| ③ 돈 보존 누수(원) | `⟨측정전⟩` |
| ③ 음수 잔액 | `⟨측정전⟩` |

## 보조: ④ 잔액 락 git-native clean delta (선택)

`f0a48d5~1`(무 @Version) vs `f0a48d5`(@Version) 단일변수 pre/post. 헤드라인 아님, 집계 옆 보조 증거.

| | 무 @Version (`f0a48d5~1`) | @Version (`f0a48d5`) |
|---|---|---|
| 잔액 Lost Update 누수(원) | `⟨측정전⟩` | `⟨측정전⟩` |

## 관찰 / 해석

`⟨측정전⟩`
