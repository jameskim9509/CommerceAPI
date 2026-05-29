# CUSTOMER Flow

`apps/web` 의 CUSTOMER 화면과 백엔드(user-api / order-api) 사이의 전체 흐름을 swim lane 형식의 flowchart 와 시나리오별 sequence diagram 으로 정리한다.

> 화면 구현: [#43](https://github.com/jameskim9509/CommerceAPI/issues/43) / [#44](https://github.com/jameskim9509/CommerceAPI/pull/44)
> 게이트웨이 라우팅: `/api/user/**` → user-api, `/api/order/**` → order-api (nginx → gateway, ADR-005 Eureka LB)

---

## 1. 전체 구성 (Swim Lane)

각 swim lane = subgraph. 노드는 책임/역할 단위로 묶었고, 점선은 부수 효과(토큰 주입, 캐시 무효화)다.

```mermaid
flowchart LR
  subgraph User["👤 사용자 (Browser)"]
    direction TB
    U1[페이지 진입]
    U2[폼 작성/제출]
    U3[상품 검색·담기·주문]
    U4[주문 상세 확인]
  end

  subgraph SPA["⚛️ apps/web (React SPA)"]
    direction TB
    S1[React Router 라우팅]
    S6[ProtectedRoute 가드]
    S2[react-hook-form + zod 검증]
    S3[TanStack Query<br/>useQuery / useMutation]
    S5[AuthContext<br/>localStorage 동기화]
    S4[axios interceptor<br/>JWT 자동 주입]
  end

  subgraph Nginx["🟢 nginx (web 컨테이너)"]
    direction TB
    N1[SPA 정적 파일 서빙<br/>SPA fallback try_files]
    N2["/api/* → gateway:80 프록시"]
  end

  subgraph GW["🚪 Spring Cloud Gateway"]
    direction TB
    G1["/user/** → lb://user-api"]
    G2["/order/** → lb://order-api"]
  end

  subgraph UA["🧑 user-api"]
    direction TB
    UA1[POST /customer/signup<br/>이메일 코드 발송]
    UA2[PUT /customer/signup/verify]
    UA3[POST /customer/login<br/>JWT 발급]
    UA4[POST /customer/balance]
  end

  subgraph OA["📦 order-api"]
    direction TB
    OA1[GET /search/product, /search/product/detail]
    OA3[GET/POST/PUT /customer/cart]
    OA4[POST /customer/cart/order<br/>Idempotency-Key]
    OA5[GET /customer/orders/:id]
  end

  subgraph Infra["💾 인프라"]
    direction TB
    I1[(MySQL: user)]
    I2[(MySQL: order)]
    I3[(Redis<br/>장바구니 · 멱등성)]
    I4{{Kafka<br/>SAGA topics}}
  end

  U1 --> S1
  U2 --> S2 --> S3
  U3 --> S3
  U4 --> S3
  S1 --> S6
  S3 --> S4
  S5 -.토큰 read.-> S4
  S4 ==HTTP==> N2
  N1 --> S1
  N2 --> G1
  N2 --> G2
  G1 --> UA1
  G1 --> UA2
  G1 --> UA3
  G1 --> UA4
  G2 --> OA1
  G2 --> OA3
  G2 --> OA4
  G2 --> OA5
  UA1 --> I1
  UA2 --> I1
  UA3 --> I1
  UA4 --> I1
  OA1 --> I2
  OA3 --> I3
  OA4 --> I3
  OA4 --> I4
  OA5 --> I2
  S3 -.invalidate.-> S3
```

**핵심 포인트**
- SPA 안의 모든 외부 호출은 `axios` 한 인스턴스를 거치고, **요청 인터셉터가 `localStorage` 의 JWT 를 `Bearer` 로 자동 주입**한다 (`apps/web/src/shared/api/client.ts`).
- 401 응답 시 인터셉터가 토큰을 지우고 `/customer/login` 으로 리다이렉트한다.
- `nginx` 는 SPA 정적 산출물 서빙과 `/api/*` 프록시 두 역할만 한다 (인증 처리 없음).
- gateway 는 service-id 기반 (`lb://user-api`, `lb://order-api`) 로라 Eureka 에서 인스턴스를 동적 발견한다 (ADR-005).

---

## 2. 시나리오별 시퀀스

회원가입부터 주문 상세 폴링까지의 시간 흐름. participant 가 swim lane 역할이고, `Note` 가 사이드 이펙트다.

```mermaid
sequenceDiagram
  autonumber
  actor User as 👤 User
  participant SPA as ⚛️ apps/web
  participant Nginx as 🟢 nginx
  participant GW as 🚪 gateway
  participant UA as 🧑 user-api
  participant OA as 📦 order-api
  participant DB as 💾 DB/Redis
  participant K as 🟧 Kafka

  rect rgb(240, 248, 255)
    Note over User,UA: 1. 회원가입 + 이메일 인증
    User->>SPA: 회원가입 폼 제출
    SPA->>SPA: zod 검증 통과
    SPA->>Nginx: POST /api/user/customer/signup
    Nginx->>GW: POST /user/customer/signup
    GW->>UA: POST /customer/signup
    UA->>DB: Customer 저장 + 인증코드 발송
    UA-->>SPA: { message }
    SPA->>SPA: /customer/signup/verify?email= 이동

    User->>SPA: 인증코드 입력 / 제출
    SPA->>Nginx: PUT /api/user/customer/signup/verify?email=&code=
    Nginx->>GW: 동일
    GW->>UA: 동일
    UA->>DB: emailVerified = true
    UA-->>SPA: { message }
    SPA->>SPA: /customer/login 이동
  end

  rect rgb(245, 250, 240)
    Note over User,UA: 2. 로그인 (JWT)
    User->>SPA: 이메일/비밀번호 제출
    SPA->>Nginx: POST /api/user/customer/login
    Nginx->>GW: 동일
    GW->>UA: 동일
    UA->>DB: 비밀번호 검증
    UA-->>SPA: JWT 문자열 (text/plain)
    SPA->>SPA: AuthContext.login(token) → localStorage 저장
    SPA->>SPA: 원래 가려던 경로 (location.state.from) 로 navigate
  end

  rect rgb(255, 250, 240)
    Note over User,OA: 3. 상품 검색 / 상세
    User->>SPA: 상품명 입력
    SPA->>Nginx: GET /api/order/search/product?name=
    Note right of SPA: axios interceptor<br/>Authorization: Bearer JWT
    Nginx->>GW: GET /order/search/product
    GW->>OA: GET /search/product
    OA->>DB: 상품 검색
    OA-->>SPA: ProductDto[]
    SPA->>SPA: card-list 렌더링

    User->>SPA: 상품 카드 클릭
    SPA->>Nginx: GET /api/order/search/product/detail?productId=
    Nginx->>GW: 동일
    GW->>OA: 동일
    OA-->>SPA: ProductDto (옵션 목록 포함)
  end

  rect rgb(255, 245, 245)
    Note over User,OA: 4. 장바구니 담기
    User->>SPA: 옵션별 수량 입력 → 담기
    SPA->>Nginx: POST /api/order/customer/cart { AddProductCartForm }
    Nginx->>GW: POST /order/customer/cart
    GW->>OA: POST /customer/cart
    OA->>DB: Redis 에 장바구니 upsert
    OA-->>SPA: Cart
    SPA->>SPA: queryClient.invalidateQueries(['cart'])<br/>/customer/cart 이동
  end

  rect rgb(250, 245, 255)
    Note over User,OA: 5. 주문 (ADR-001 멱등성)
    User->>SPA: 수량 조정 → 주문하기
    SPA->>SPA: crypto.randomUUID() 로 Idempotency-Key 생성
    SPA->>Nginx: POST /api/order/customer/cart/order<br/>Header: Idempotency-Key
    Nginx->>GW: 동일
    GW->>OA: 동일
    OA->>DB: IdempotencyService.execute(key)
    OA->>DB: Order 저장 (PENDING)
    OA->>K: Kafka publish (ADR-003 SAGA: stock.reserve)
    OA-->>SPA: { orderId, status: PENDING, ... }
    SPA->>SPA: /customer/orders/:orderId 이동
  end

  rect rgb(240, 255, 240)
    Note over User,OA: 6. 주문 상세 (SAGA 폴링)
    loop status ∈ { PENDING, PAID } 동안 2초 간격
      SPA->>Nginx: GET /api/order/customer/orders/:id
      Nginx->>GW: 동일
      GW->>OA: 동일
      OA->>DB: Order 조회
      OA-->>SPA: OrderDto
    end
    Note right of K: SAGA: stock.reserved → payment.charged<br/>→ CONFIRMED 또는 보상 → FAILED
  end

  rect rgb(255, 255, 235)
    Note over User,UA: 7. 잔액 충전
    User->>SPA: 금액/메모/출처 입력
    SPA->>Nginx: POST /api/user/customer/balance
    Nginx->>GW: 동일
    GW->>UA: POST /customer/balance
    UA->>DB: CustomerBalanceHistory 저장
    UA-->>SPA: { balance }
  end

  Note over SPA: ⚠️ 토큰 만료 / 401 응답 시<br/>axios interceptor → localStorage 삭제 → /customer/login 강제 이동
```

---

## 3. 화면 ↔ API 매핑

| 화면 (route) | 컴포넌트 | 메서드 + 경로 | 가드 |
|---|---|---|---|
| `/customer/signup` | `Signup.tsx` | `POST /api/user/customer/signup` | 없음 |
| `/customer/signup/verify` | `SignupVerify.tsx` | `PUT  /api/user/customer/signup/verify` | 없음 |
| `/customer/login` | `Login.tsx` | `POST /api/user/customer/login` | 없음 |
| `/customer/products` | `Products.tsx` | `GET  /api/order/search/product?name=` | CUSTOMER |
| `/customer/products/:id` | `ProductDetail.tsx` | `GET  /api/order/search/product/detail` + `POST /api/order/customer/cart` | CUSTOMER |
| `/customer/cart` | `Cart.tsx` | `GET/PUT /api/order/customer/cart` + `POST /api/order/customer/cart/order` (`Idempotency-Key`) | CUSTOMER |
| `/customer/orders` | `Orders.tsx` | (입력 폼만, 백엔드 호출 없음) | CUSTOMER |
| `/customer/orders/:orderId` | `OrderDetail.tsx` | `GET  /api/order/customer/orders/:id` (PENDING/PAID 동안 2 s 폴링) | CUSTOMER |
| `/customer/balance` | `Balance.tsx` | `POST /api/user/customer/balance` | CUSTOMER |

---

## 4. 관련 ADR

- **ADR-001**: 멱등성 키로 결제·주문 중복 방지. SPA 가 `crypto.randomUUID()` 로 키를 만들어 `POST /customer/cart/order` 헤더에 실음.
- **ADR-003**: Choreography SAGA. 주문 생성 직후 응답은 PENDING; SPA 가 `OrderDetail` 에서 2 초 폴링으로 CONFIRMED/FAILED 까지 추적.
- **ADR-005**: Eureka + gateway lb 라우팅. SPA → nginx → gateway → `lb://user-api` / `lb://order-api`.

## 5. 알려진 한계

- `ProductDto` 에 `sellerId` 가 없어 `AddProductCartForm.sellerId` 를 임시로 `0` 전송 (백엔드 응답 보완 후 정정 필요).
- 백엔드에 주문 목록 API 가 없어 `/customer/orders` 는 ID 입력 폼만 제공.
- SELLER 화면은 [#50](https://github.com/jameskim9509/CommerceAPI/issues/50) 으로 분리, `docs/seller-flow.md` 참조.
