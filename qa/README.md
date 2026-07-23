# qa/ — 시나리오별 QA 측정 하네스

`qa/` 는 시나리오별 폴더로 구성된다. 각 폴더는 자립형(self-contained)이라
자기 폴더 안의 compose·seed·k6·스크립트만으로 실행된다.

| 시나리오 | 목적 | 진입점 | 결과 |
|---|---|---|---|
| [**01-orderapi-load-balancing/**](01-orderapi-load-balancing/) | orderApi 1→2→4 스케일 시 **LoadBalancer 효과** 정량화 (ADR-005 시나리오 3) | 수동 측정 절차 ([README](01-orderapi-load-balancing/README.md)) | `E1/E2/E3` |
| **02-consistency/** _(예정)_ | 멱등성·낙관적락(초과판매)·SAGA보상 통합 정합성 검증 ([ADR-008](../ADR/008-order-consistency-integration-scenario.md)) | ADR-008 명세대로 **신규 작성 예정** | — |

> **02-consistency 는 아직 없다.** [ADR-008](../ADR/008-order-consistency-integration-scenario.md) 명세(주문 10만 건 통합 부하 + 주변 장애 T1~T6)에 맞춰 새로 작성한다.
> 그 전까지 각 정합성 메커니즘의 개별 검증은 부하가 아니라 각 모듈 `src/test` 의 단위·통합 테스트가 담당한다.

## 빠른 시작

측정은 **인스턴스를 직접 늘려가며 수동으로** 수행한다. 시나리오 폴더 README 의
"실행 — 수동 측정" 절차(스택 기동 → 시드 → 모니터 → k6 → 수집 → 분석)를 따른다:

[01-orderapi-load-balancing/README.md](01-orderapi-load-balancing/README.md)

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
- Windows 는 Git Bash / WSL2. Git Bash 는 k6 컨테이너 경로(`/scripts`, `/results`) 보호를 위해 `export MSYS_NO_PATHCONV=1` 를 먼저 실행 (WSL2 는 불필요).
- k6 를 `docker compose run` 으로 부를 때 **`--no-deps` 필수** — 없으면 의존성 트리가 다시 뜨며
  `--scale orderapi=N` 이 기본값 1 로 리셋된다 (초기 측정을 통째로 무효화했던 인프라 버그).
