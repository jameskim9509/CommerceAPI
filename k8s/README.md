# Kubernetes 배포 (백엔드)

CommerceAPI 백엔드(eureka · gateway · userApi · orderApi)를 Kubernetes 로 배포하는 매니페스트.
설계 배경·리소스 사용 계획은 [ADR-006](../ADR/006-kubernetes-deployment-scalability.md) 참고.

## 구조 (kustomize base + overlays)

```
k8s/
├── base/                       # 공통(앱) — 플랫폼 중립
│   ├── namespace.yaml          # namespace: commerce
│   ├── configmap.yaml          # commerce-config (비밀 아닌 설정, test 기본값)
│   ├── eureka.yaml             # Deployment + Service
│   ├── gateway.yaml            # Deployment + Service
│   ├── user-api.yaml           # Deployment + Service
│   ├── order-api.yaml          # Deployment + Service
│   └── kustomization.yaml
└── overlays/
    ├── test/                   # 로컬 k8s · SPRING_PROFILES_ACTIVE=test
    │   ├── infra/              # mysql-user · mysql-order · redis · kafka (in-cluster)
    │   ├── secret.yaml         # 더미 자격증명
    │   ├── ingress-nginx.yaml  # 외부 진입 Ingress (ingressClassName: nginx)
    │   └── kustomization.yaml  # replicas=1, HPA 없음
    └── prod/                   # EKS · SPRING_PROFILES_ACTIVE=prod
        ├── secret.yaml         # placeholder (실제는 External Secrets)
        ├── configmap-patch.yaml# Kafka/Redis → 관리형 엔드포인트
        ├── hpa.yaml            # gateway 2~6 · user-api 2~6 · order-api 3~10
        ├── ingress-alb.yaml    # ALB Ingress
        └── kustomization.yaml  # ECR 이미지, RDS host 패치, imagePullPolicy=Always
```

> **네임스페이스는 `commerce` 고정.** `overlays/test/infra/kafka.yaml` 의 advertised listener 가
> `kafka-0.kafka.commerce.svc.cluster.local` 을 가정한다. 네임스페이스를 바꾸면 그 값도 수정.

빌드 미리보기(적용 없이 렌더만):

```bash
kubectl kustomize k8s/overlays/test
kubectl kustomize k8s/overlays/prod
```

---

## test — 로컬 k8s (minikube / kind / Docker Desktop)

`docker-compose.test.yml` 의 쿠버네티스 판. 상태 저장소(MySQL×2·Redis·Kafka)를 클러스터 안에 함께 띄운다.
외부 진입은 gateway 를 ClusterIP 로 두고 **nginx Ingress** 로 노출한다 — prod 의 ALB Ingress 경로를 로컬에서 그대로 재현한다.

### 사전 1회 — Ingress 진입 준비 (클러스터 애드온, kind 기준)

`ingress-nginx` 컨트롤러와, kind 에서 LoadBalancer 의 EXTERNAL-IP 를 채워줄 `cloud-provider-kind` 를 설치한다.
(다른 로컬 클러스터면 해당 LB 수단을 쓰거나, 아래 3) 의 port-forward 폴백을 사용)

```bash
# ingress-nginx 컨트롤러 — cloud 변형(컨트롤러 Service 가 type: LoadBalancer). 태그는 최신 stable 로 교체 가능
# (ingress-nginx 네임스페이스에 설치 — commerce 와 분리된 공유 클러스터 애드온)
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.11.3/deploy/static/provider/cloud/deploy.yaml
kubectl -n ingress-nginx wait --for=condition=Available deploy/ingress-nginx-controller --timeout=180s

# cloud-provider-kind — LoadBalancer EXTERNAL-IP 프로비저너(클러스터 밖 호스트 docker 프록시). kind 전용
go install sigs.k8s.io/cloud-provider-kind@latest   # 또는 GitHub releases 바이너리
```

### 1) 이미지 빌드 + 로컬 클러스터에 로드

이미지 태그는 `base/` 의 이미지 이름과 일치해야 한다(`commerce-<module>:latest`).

```bash
# jar 빌드 (repo 루트)
./gradlew clean build -x test

# 이미지 빌드 (각 모듈의 Dockerfile 은 build/libs/<module>-0.1.jar 를 기대)
docker build -t commerce-eureka  ./eurekaServer
docker build -t commerce-gateway ./gateway
docker build -t commerce-userapi ./userApi
docker build -t commerce-orderapi ./orderApi

# 로컬 클러스터로 로드 (택1)
# kind:
for i in commerce-eureka commerce-gateway commerce-userapi commerce-orderapi; do kind load docker-image $i:latest; done
# minikube:
for i in commerce-eureka commerce-gateway commerce-userapi commerce-orderapi; do minikube image load $i:latest; done
```

### 2) 배포

```bash
kubectl apply -k k8s/overlays/test
kubectl -n commerce get pods -w
```

기동 순서는 별도 제어하지 않는다 — MySQL 이 늦으면 앱이 몇 번 재시작(CrashLoopBackOff) 후 정상화된다(startupProbe + restartPolicy).

### 3) 접근 — nginx Ingress 경유

`cloud-provider-kind` 를 **관리자/elevated 권한**으로 실행해 두면(포그라운드, LoadBalancer 감시)
컨트롤러 Service 에 EXTERNAL-IP 가 채워진다.

```bash
cloud-provider-kind        # Windows: "관리자 권한" 셸 / macOS·WSL2: sudo cloud-provider-kind
kubectl -n ingress-nginx get svc ingress-nginx-controller   # EXTERNAL-IP 채워지는지 확인
```

- **Linux**: EXTERNAL-IP 가 호스트에서 바로 닿는다 → `curl http://<EXTERNAL-IP>/user/...`
- **Windows/Mac(Docker Desktop)**: docker 브리지 IP 가 호스트에서 안 닿아, cloud-provider-kind 가
  `kindccm-…` 프록시 컨테이너를 띄워 **호스트 포트로 매핑**한다. EXTERNAL-IP 가 아니라 그 매핑 포트로 접근:

```bash
docker ps --filter name=kindccm --format "{{.Names}} {{.Ports}}"
# 예: kindccm-...  0.0.0.0:53412->80/tcp  →  curl http://localhost:53412/user/...
```

폴백(클러스터·OS 무관, 고정 포트) — 컨트롤러로 port-forward:

```bash
kubectl -n ingress-nginx port-forward svc/ingress-nginx-controller 8080:80
# → curl http://localhost:8080/user/... , http://localhost:8080/order/...
```

### 4) 정리

```bash
kubectl delete -k k8s/overlays/test
# PVC(StatefulSet 데이터)는 남으니 필요 시:
kubectl -n commerce delete pvc --all
```

---

## prod — EKS

상태 저장소는 클러스터 밖 관리형(RDS · ElastiCache · MSK). 적용 전에 아래를 채운다.

### 사전 요구사항 (클러스터)

| 구성 | 용도 |
|---|---|
| metrics-server | HPA(CPU 메트릭) |
| AWS Load Balancer Controller | `ingress-alb.yaml` → ALB 프로비저닝 |
| Cluster Autoscaler 또는 Karpenter | Node 층 자동 증감 |
| EBS CSI Driver | (이 매니페스트는 prod 에 PVC 없음 — 관리형 DB 사용) |

### 채워야 할 placeholder (`REPLACE_WITH_*`)

| 위치 | 값 |
|---|---|
| `overlays/prod/kustomization.yaml` | user-api / order-api `MYSQL_HOST` → 각 RDS 엔드포인트 |
| `overlays/prod/configmap-patch.yaml` | `KAFKA_BOOTSTRAP_SERVERS`(MSK), `REDIS_HOST`(ElastiCache) |
| `overlays/prod/secret.yaml` | `MYSQL_USER/PASSWORD`, `MAILGUN_APIKEY/DOMAIN` (가급적 External Secrets 로 대체) |
| `overlays/prod/kustomization.yaml` `images:` | ECR 레지스트리/태그 |

> **시크릿**: `secret.yaml` 평문 커밋은 피하고 External Secrets Operator / AWS Secrets Manager / SSM 로 주입 권장.

### 배포

```bash
# 이미지가 ECR 에 있어야 한다 (commerce-eureka 포함 — 아래 주의 참고)
kubectl apply -k k8s/overlays/prod
kubectl -n commerce get pods,hpa,ingress -w
# ALB 주소:
kubectl -n commerce get ingress commerce
```

---

## 주의 / 남는 작업 (ADR-006 "남는 책임")

- **commerce-eureka 이미지** — 현재 CICD 는 gateway/userapi/orderapi 3개만 ECR push 한다. eureka 이미지도 push 단계 추가 필요(또는 디스커버리를 k8s native 로 전환 — 별도 ADR).
- **Eureka 유지** — k8s Service+DNS 가 디스커버리/LB 를 native 제공하므로 Eureka 는 중복이나, ADR-005·`qa/` 자산 보존을 위해 유지. gateway 는 `lb://`(Eureka) 로 pod IP 에 직접 라우팅한다.
- **probe 는 TCP** — actuator 미도입이라 포트 개방만 확인. DB/Kafka/Redis 연결을 반영하는 readiness 는 `spring-boot-starter-actuator` + `/actuator/health/{readiness,liveness}` 도입 후 권장.
- **Kafka 단일 노드** — KRaft 1 브로커는 SPOF. 운영은 MSK.
- **prod 프로파일** — userApi·orderApi 는 `application-prod.yml`(env 외부화 + 운영 설정). gateway·eureka 는 base `application.yml` 사용.
