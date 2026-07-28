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
├── chaos-schedule.sh            주변 장애 주입
├── quiescence-gate.sh           비동기 흐름 종료 대기
├── verify-consistency.sh        정합성 검증
├── aggregate-runs.sh            run 결과 집계
├── k6/load-test-consistency.js  통합 시나리오 스크립트
├── seed/                        시드 데이터
│   ├── user.sql
│   └── order.sql
└── results/                     실행 산출물
```

## 사전 요구사항

- RAM 8GB / 8CPU 여유공간
- WSL2 또는 Bash
- Docker (compose 환경)

## 환경변수

모두 `docker-compose.qa.yml` 이 `${VAR:-기본값}` 으로 읽는다. `.env` 파일은 없으므로 **셸 `export` 로만** 주입되고, 지정하지 않으면 기본값이 그대로 쓰인다. 값 변경은 컨테이너 재생성(`down -v` → `up -d`) 이후에만 반영된다 — 이미 떠 있는 컨테이너에는 적용되지 않는다.

| 변수                 | 기본값    | 의미                                                       |
| -------------------- | --------- | ---------------------------------------------------------- |
| `ORDERAPI_REPLICAS`  | `4`       | orderApi 인스턴스 수 (× Kafka 4파티션)                     |
| `USERAPI_REPLICAS`   | `2`       | userApi 인스턴스 수 (SAGA 결제 컨슈머 처리량 상한)         |
| `DB_POOL_SIZE`       | `30`      | HikariCP `maximumPoolSize` (양 서비스 공통)                |
| `DB_CONN_TIMEOUT_MS` | `1000`    | HikariCP `connectionTimeout`(ms) — **T2 재현용**           |
| `ORDER_TARGET`       | `100000`  | k6 목표 주문 수                                            |
| `ARRIVAL_RATE`       | `200`     | k6 도착률(RPS)                                             |
| `RICH_POOL`          | `300`     | 시드 `ctrich` 고객 풀 크기                                 |
| `RUN_LABEL`          | `unknown` | 산출물 파일명 접두 (`results/$RUN_LABEL-*`)                |

### `DB_CONN_TIMEOUT_MS` — 왜 1000ms 인가

주변 장애 **T2(에러 발생시 10회 재시도 후 이벤트 드롭)** 를 실제로 발화시키기 위한 QA 전용 값이다.

- Kafka 기본 `DefaultErrorHandler` = `FixedBackOff(0L, 9)` = **지연 없이 총 10회 시도**.
- 리스너 1회 시도의 소요 = 커넥션 획득 블로킹 = **HikariCP `connectionTimeout`**.
- 따라서 **재시도 창 ≈ 10 × connectionTimeout**. 이 창이 **실제 DB 불가 시간(≈21초)** 보다 짧아야 재시도가 소진되고 log-and-commit 드롭(= 이벤트 영구 유실)이 일어난다.
  - `30000ms`(HikariCP 기본, 무설정): 재시도 창 300초 ≫ 21초 → 첫 시도가 DB 복구까지 그냥 대기했다 성공 → **예외 자체가 안 나므로 재시도도 드롭도 0**. 실측으로도 `T-noT4` 런에서 `v3a`(PENDING/PAID 잔여)=0 이었다.
  - `1000ms`: 재시도 창 ≈10초 < 21초 → 드롭 재현.
- 커밋 `f426df4` 는 반대 방향으로 튜닝했다 — `T2_DOWN_S` 를 25→10초로 낮춰 DB 불가 시간을 `connectionTimeout`(30초) **안쪽**에 넣어 대기 요청 몰살(실측 530건)을 막고 부하 예산을 지켰다. 그 부작용으로 T2 결함이 통째로 잠들었다. 여기서는 `T2_DOWN_S` 를 되돌리는 대신 `connectionTimeout` 을 낮춰, 부하 예산은 유지한 채 재시도 창만 불가 구간 안으로 넣는다.

> **하한 250ms**: HikariCP 는 250 미만이면 `IllegalArgumentException`(→ Spring 바인딩 실패 → 앱 기동 불가)이거나 `30000ms` 로 되돌린다. **`0` 은 "즉시 실패"가 아니라 무한 대기**이므로 절대 쓰지 말 것.
>
> **가드**: 카오스 없는 스모크(`C-smoke`/`T-smoke`)에서 `v3a` 가 **0 이 아니면 이 값이 너무 공격적이라는 신호**다. T2 와 무관하게 평상시 부하만으로 커넥션 획득이 타임아웃하고 있는 것이므로 `DB_CONN_TIMEOUT_MS` 를 올려(2000 → 5000) 스모크 `v3a=0` 을 회복한 뒤 본 런을 돌린다.
>
> **비교 주의**: 기존 런 `C-run1`~`C-run3` · `T-run1`~`T-run2` · `T-noT4` 는 **이 설정 없이(= 30000ms) 측정**됐다. 그 결과들과 `DB_CONN_TIMEOUT_MS` 적용 후의 런은 T2 발화 여부가 달라 `N`/`r`/`money_leak` 을 직접 비교하면 안 된다. 새 값으로는 **control/treatment 양 arm 을 모두 다시 측정**해야 `N→r` 이 성립한다.
>
> `connectionTimeout` 을 5000ms 미만으로 두면 HikariCP 가 `validationTimeout`(기본 5000ms)을 같은 값으로 자동 하향한다 — 의도된 부작용이며 양 arm 에 동일하게 적용된다.

#### 부작용 — JDBC 로그인 타임아웃도 함께 내려간다 (기동 크래시루프 위험)

`connectionTimeout` 은 커넥션 **획득 대기**만 제어하는 값이 아니다. HikariCP 는 풀을 만들 때 `dataSource.setLoginTimeout(max(1, (500 + connectionTimeout) / 1000)초)` 를 건다(`PoolBase.setLoginTimeout`). 즉 **30000ms → 30초**였던 JDBC 로그인 타임아웃이 **1000ms → 1초**가 된다(`DriverDataSource` 는 이를 `DriverManager` 전역 static 에 반영한다).

그리고 풀 초기화는 fail-fast 다(`HikariConfig.initializationFailTimeout=1` 기본). 첫 물리 커넥션을 1초 안에 못 잡으면 `PoolInitializationException` → 컨텍스트 기동 실패 → **컨테이너 크래시루프 → 런 전체 무효**다. 위험을 키우는 조건:

- `minIdle` 미설정 시 `maxPoolSize` 와 같아진다 → `DB_POOL_SIZE=30` × (orderapi 4 + userapi 2 replicas) = **180 커넥션이 기동 직후 동시에** 채워진다.
- compose 의 healthcheck `mysqladmin ping` 은 mysqld 워밍업 완료를 보장하지 않는다 → `service_healthy` 만으로는 "1초 안에 로그인 완료"가 보장되지 않는다.
- T2 는 런 도중 `mysql-order` 를 stop/start 한다 → 복구 직후 크래시 리커버리 구간에도 같은 1초 제한이 걸린다.

> **필수 확인**: `up -d` 직후 아래로 6개 앱 컨테이너가 전부 정상 기동했는지 본다(실행 절차 3단계에 포함).
>
> ```bash
> docker compose -f docker-compose.qa.yml logs orderapi userapi \
>   | grep -i 'PoolInitializationException\|Exception during pool initialization\|Start completed'
> ```
>
> `PoolInitializationException` 이 보이거나 `Start completed` 가 6개(orderapi 4 + userapi 2) 미만이면 `DB_CONN_TIMEOUT_MS=2000` 으로 올려 다시 띄운다 — `10 × 2000 = 20s < 21s` 라 **T2 재현 조건은 그대로 만족**하면서 로그인 타임아웃만 2초로 완화된다.

## 실행

**무방어 브랜치**와 **방어 브랜치**에서 같은 절차를 그대로 돌려 두 측정을 얻는다.

1. 브랜치 변경 및 이미지 빌드

```bash
git checkout v2                                  # 무방어(control) — control/no-defense 의 하네스 갱신본
cd qa/02-consistency
docker compose -f docker-compose.qa.yml build    # 이미지 재빌드
```

> **태그 세대 주의**: `v1` 은 구 하네스(T2 미발화 · 폴트 귀속 계수 없음 · N 산식 이중 계상) 시점의 control 이다.
> 현재 하네스로 측정하려면 반드시 **`v2`** 를 쓴다. `v1` 은 과거 측정의 재현용으로만 남겨둔다.
>
> **arm 전환 시 재빌드를 피하려면** 양 arm 이미지를 미리 구워 태그해 두고 `:latest` 로 승격만 하면 된다.
> ADR-008 이 요구하는 interleaved 반복(T→C→T→C…)은 매번 재빌드하면 비현실적이다:
>
> ```bash
> git checkout v2   && docker compose -f docker-compose.qa.yml build orderapi userapi
> docker tag consist-orderapi consist-orderapi:c && docker tag consist-userapi consist-userapi:c
> git checkout main && docker compose -f docker-compose.qa.yml build orderapi userapi   # 방어 브랜치
> docker tag consist-orderapi consist-orderapi:t && docker tag consist-userapi consist-userapi:t
>
> # 이후 런 직전에 승격만 (eureka/gateway 는 arm 간 동일해 재빌드 불필요)
> docker tag consist-orderapi:t consist-orderapi:latest && docker tag consist-userapi:t consist-userapi:latest
> ```
>
> QA 하네스는 양 arm 바이트 동일해야 하므로, 하네스를 고치면 **반드시 양 브랜치에 같은 커밋을 반영**하고
> `git diff --stat <control> <treatment> -- qa/02-consistency/` 가 비는지 확인할 것.

2. 통합 시나리오 실행전 스모크 테스트 — 빌드/세팅 정상동작 확인

results/$LABEL-verify.txt 의 N 값이 판정기준에 맞음을 확인.

```bash
LABEL=C-smoke                   # 방어버전은 T-smoke
export ORDERAPI_REPLICAS=4
export DB_CONN_TIMEOUT_MS=1000  # T2 재현용 (기본값과 동일 — 값을 바꿔 실험할 때만 지정)

# 스택 기동
docker compose -f docker-compose.qa.yml down -v --remove-orphans
docker compose -f docker-compose.qa.yml up -d \
    mysql-user mysql-order redis kafka eureka userapi orderapi gateway db-seed

# 시드 + 레지스트리 전파 대기
docker compose -f docker-compose.qa.yml wait db-seed
sleep 60                                           

# ★ 앱 기동 확인 (DB_CONN_TIMEOUT_MS 의 로그인 타임아웃 부작용 — 아래 '부작용' 절 참고)
docker compose -f docker-compose.qa.yml logs orderapi userapi \
    | grep -i 'PoolInitializationException\|Exception during pool initialization\|Start completed'

# 카오스 없이 작은 부하만
RUN_LABEL=$LABEL ORDER_TARGET=2000 ARRIVAL_RATE=50 \
    docker compose -f docker-compose.qa.yml run --rm --entrypoint sh k6
k6 run /scripts/load-test-consistency.js
exit

# 비동기 흐름 종료 대기 → 검증
bash quiescence-gate.sh
RUN_LABEL=$LABEL bash verify-consistency.sh
docker compose -f docker-compose.qa.yml down -v
```

- 판정기준

| 브랜치           | v1 (멱등)       | v2 (초과판매) | v3a(PENDING/PAID) |
| ---------------- | --------------- | ------------- | ----------------- |
| 방어 (T-smoke)   | 0               | 0             | 0                 |
| 무방어 (C-smoke) | > 0 (재전송 수) | 0             | 0                 |

> 무방어 버전은 재전송 시나리오(2%)가 중복 주문을 만들어 v1>0 이 정상
>
> **`v3a`는 `DB_CONN_TIMEOUT_MS` 의 가드 지표이기도 하다.** 스모크는 카오스가 없으므로 T2 가 발화할 수 없다 — 그럼에도 `v3a > 0` 이면 평상시 부하만으로 커넥션 획득이 타임아웃해 이벤트가 드롭되고 있다는 뜻이니, `DB_CONN_TIMEOUT_MS` 를 올려(2000 → 5000) `v3a=0` 을 회복한 뒤 본 런으로 넘어간다.

4. 시나리오 실행 1)~8). (라벨만 바꿔가며 반복 - 무방어 `C-run1`, `C-run2` … / 방어 `T-run1` …).

```bash
LABEL=C-run1      
export ORDERAPI_REPLICAS=4      # compose deploy.replicas 로 주입
export DB_CONN_TIMEOUT_MS=1000  # HikariCP connectionTimeout — T2(10회 재시도 후 드롭) 재현 조건

# 1) 이전 잔재 정리
docker compose -f docker-compose.qa.yml down -v --remove-orphans

# 2) QA 스택 기동 (k6 제외)
docker compose -f docker-compose.qa.yml up -d \
    mysql-user mysql-order redis kafka eureka userapi orderapi gateway db-seed

# 3) 시드 + 레지스트리 전파 대기
docker compose -f docker-compose.qa.yml wait db-seed
sleep 60                                           

# 3-1) ★ 앱 기동 확인 — DB_CONN_TIMEOUT_MS 가 JDBC 로그인 타임아웃까지 낮추므로
#      풀 초기화(fail-fast)가 실패해 크래시루프에 빠지면 런 전체가 무효가 된다.
#      'Start completed' 가 6개(orderapi 4 + userapi 2) 미만이거나 PoolInitializationException 이
#      보이면 DB_CONN_TIMEOUT_MS=2000 으로 올리고 1) 부터 다시. (10×2000=20s<21s 라 T2 재현은 유지)
docker compose -f docker-compose.qa.yml logs orderapi userapi \
    | grep -i 'PoolInitializationException\|Exception during pool initialization\|Start completed'
docker compose -f docker-compose.qa.yml ps orderapi userapi

# 4) 주변 장애 주입 스케줄 실행
ORDER_TARGET=100000 bash chaos-schedule.sh > results/$LABEL-chaos.log 2>&1 &
CHAOS_PID=$!                                

# 5) k6 셸 진입
RUN_LABEL=$LABEL ORDER_TARGET=100000 ARRIVAL_RATE=200 \
    docker compose -f docker-compose.qa.yml run --rm --entrypoint sh k6

# 6) k6 시나리오 실행 및 종료
k6 run /scripts/load-test-consistency.js
exit

# 7) 카오스 종료 → 비동기 흐름 종료 대기 → 검증
kill $CHAOS_PID 2>/dev/null                 
bash quiescence-gate.sh                                 # 비동기 흐름 종료 대기
RUN_LABEL=$LABEL bash verify-consistency.sh             # 판정 결과 확인

# 8) 정리 후 다음 run 으로
docker compose -f docker-compose.qa.yml down -v
```

5. 반복된 결과 집계

```bash
./aggregate-runs.sh C           # 무방어 부랜치
./aggregate-runs.sh T           # 방어 브랜치
```

> 반복적으로 시나리오를 돌림으로써 현재 컴퓨터의 상태별 예외 상황에 대한 오차를 줄임
>
> ⚠️ **표본 혼입 주의**: `C-run1`~`C-run3` · `T-run1`~`T-run2` · `T-noT4` 는 `DB_CONN_TIMEOUT_MS` 도입 **이전**(= HikariCP 기본 30000ms)에 측정된 것이라 T2 가 발화하지 않은 런이다. `aggregate-runs.sh` 는 `<PREFIX>-run*` 라벨을 무조건 긁어 중앙값을 내므로, 새 설정으로 다시 돌린 런을 같은 `runN` 번호 체계에 이어 붙이면 조건이 다른 표본이 섞인다. 새 설정으로는 **양 arm 을 처음부터 다시 측정**하고, 기존 결과 파일은 별도 보관하거나 라벨 접두를 분리할 것.

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
2. 비동기 흐름 종료 대기
3. DB 불변식 위반 개수 체크 (① + ② + ③)

| 불변식               | 위반 개수 체크                                                                             |
| -------------------- | ------------------------------------------------------------------------------------------ |
| ① 멱등성            | duplicate_order_responses 건수 (k6)                                                        |
| ② 초과판매          | (count<0 행 수) +\|초기재고−현재재고−CONFIRMED 수량\| + max(0, CONFIRMED 수량−초기재고) |
| ③ 결제·재고 정합성 | (PENDING/PAID 행 수) + (outbox 미발행 행 수, 양 DB) + (음수 잔액 행 수)                    |

> **돈 누수** = (초기잔액합 − 현재잔액합) − Σ CONFIRMED total_price
