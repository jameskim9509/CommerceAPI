# qa/ — 시나리오별 QA 측정 하네스

`qa/` 는 시나리오별 폴더로 구성된다. 각 폴더는 자립형(self-contained)이라
자기 폴더 안의 compose·seed·k6·스크립트만으로 실행된다.

| 시나리오 | 목적 | 진입점 | 결과 |
|---|---|---|---|
| [**01-load-balancing/**](01-load-balancing/) | orderApi 1→2→4 스케일 시 **LoadBalancer 효과** 정량화 (ADR-005 시나리오 3) | `run-experiments.sh` / `run-experiments-order-only.sh` | `E1/E2/E3`, `E1o/E2o/E3o` |
| [**02-consistency/**](02-consistency/) | 6종 방어(멱등·재고락·SAGA보상·아웃박스·잔액락·dedup)의 통합 정합성 — 무방어(N) vs 방어(r) 집계 KPI + 교차 DB 불변식 ([ADR-008](../ADR/008-order-consistency-integration-scenario.md)) | `run-kpi-matrix.sh` / `run-consistency.sh` | `KPI-MATRIX.md`, `*-verify.txt` (전부 `⟨측정전⟩`) |

> **02-consistency 는 실행 가능한 하네스로 구현됐다** (ADR-008 명세: 주문 10만 건 통합 부하 + 원하는 장애 6종 + 주변 장애 T1~T4).
> 단, 측정치는 전부 `⟨측정전⟩` — `run-kpi-matrix.sh` 를 실제로 돌린 뒤 채운다. 상세는 [02-consistency/README.md](02-consistency/README.md).

## 빠른 시작

```bash
# 시나리오 ① 부하 분산 (E1 → E2 → E3)
./qa/01-load-balancing/run-experiments.sh
```

각 시나리오의 상세(측정 모델·합격 기준·구조)는 폴더 안 README 참조:
[01-load-balancing/README.md](01-load-balancing/README.md)

## 공통 규칙

- **인프라는 시나리오마다 자기 복제본을 가진다** — 각 폴더가 자기 `docker-compose.qa.yml` 사본을 두고,
  프로젝트명(`name:`)을 달리해 컨테이너·볼륨 네임스페이스를 분리한다 (한 번에 하나씩 8GB/8CPU 스택 기동 전제).
- **QA 시나리오는 자립 시드를 가진다** — 각 시나리오 폴더의 `seed/` 는 그 시나리오가 필요한
  데이터(seller·부하 더미 등)를 스스로 만든다. 다른 시나리오나 상시환경에 의존하지 않는다.
- **상시 테스트 환경의 공통 베이스는 루트 [seed/](../seed/) 에 있다** — 루트
  [docker-compose.test.yml](../docker-compose.test.yml) 과 [k8s/overlays/test](../k8s/overlays/test) 의
  db-seed 가 이 경로를 **단일 출처**로 참조한다. QA 시나리오와는 분리돼 있어, 서로 수정해도 영향이 없다.

## 공통 주의사항

- Docker daemon + 약 8GB 메모리 / 8CPU 필요 (4 인스턴스 + MySQL×2 + Kafka 동시 기동).
- Windows 는 Git Bash / WSL2 에서 실행 (스크립트가 `MSYS_NO_PATHCONV=1` 로 경로 변환 회피).
- k6 를 `docker compose run` 으로 부를 때 **`--no-deps` 필수** — 없으면 의존성 트리가 다시 뜨며
  `--scale orderapi=N` 이 기본값 1 로 리셋된다 (초기 측정을 통째로 무효화했던 인프라 버그).
