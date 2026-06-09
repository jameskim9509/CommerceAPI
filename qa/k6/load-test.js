// =============================================================================
// ADR-005 시나리오 3: orderApi 다중 인스턴스 부하 분산 효과 측정
//
// 전제 (qa/seed/functional → load 순서로 시드 완료):
//   - userApi DB: customer{1..1000}@qa.test, password "password", verify=true, balance=10_000_000  (load/user.sql)
//   - orderApi DB: 100 products × 5 product_items (재고 1_000_000 각각, id 1..500)               (load/order.sql)
//   - seller_id=1 은 functional/user.sql 이 만든 seller1 (functional 이 먼저 주입됨)
//
// 워크로드:
//   - ramping-vus 0 → 50 (warm-up 30s) → 200 (ramp 1m) → 200 (steady 3m) → 0 (cool-down 30s)
//   - 각 VU 반복: 로그인(캐시) → 카트 추가 → 주문 (주문만 p99 측정)
//   - 5% 확률로 같은 Idempotency-Key 재전송 (멱등성 검증)
//
// 환경변수:
//   - GATEWAY_URL (기본: http://gateway)
//   - USER_COUNT (기본: 1000, 시드한 사용자 수와 맞춰야 함)
//   - PRODUCT_ITEM_BASE_ID (기본: 1, 시드한 첫 product_item id)
//   - EXPERIMENT_LABEL (예: E1, E2, E3 — 결과 파일 이름에 사용)
// =============================================================================

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const GATEWAY_URL = __ENV.GATEWAY_URL || 'http://gateway';
const USER_COUNT = parseInt(__ENV.USER_COUNT || '1000');
const PRODUCT_ITEM_BASE_ID = parseInt(__ENV.PRODUCT_ITEM_BASE_ID || '1');
const EXPERIMENT_LABEL = __ENV.EXPERIMENT_LABEL || 'unknown';

// 커스텀 메트릭
const orderLatency = new Trend('order_create_latency', true);
const instanceHits = new Counter('instance_hits');
const idempotencyReplays = new Counter('idempotency_replay_attempts');
const duplicateOrderResponses = new Counter('duplicate_order_responses');

// ADR-005 LB 분배 검증: X-Instance-Id 별 별도 카운터를 동적으로 생성하기 어려우므로
// 모든 hits 를 명시 라벨 4 개로 분기 집계. 라벨에 매핑 안 되는 인스턴스는 'other' 로.
const hitsBackend1 = new Counter('hits_backend1');
const hitsBackend2 = new Counter('hits_backend2');
const hitsBackend3 = new Counter('hits_backend3');
const hitsBackend4 = new Counter('hits_backend4');
const hitsOther    = new Counter('hits_other');
const instanceIdSeen = new Trend('instance_id_seen');  // dummy: not used but kept for compat

function recordInstanceHit(instanceId) {
    instanceHits.add(1);
    if (!instanceId) { hitsOther.add(1); return; }
    // 컨테이너 이름은 docker compose 가 부여 (예: qa-orderapi-1) — k6 가 받는 HOSTNAME 환경변수는 컨테이너 이름이 아니라 컨테이너 ID (12자리 hex).
    // 여기서는 본 측정용으로 5분 동안 등장하는 unique instance id 분포 자체를 별도 객체에 누적 후 handleSummary 에서 dump.
    if (!__ENV.__INSTANCE_MAP) { /* state lives in module scope below */ }
    INSTANCE_HITS_MAP[instanceId] = (INSTANCE_HITS_MAP[instanceId] || 0) + 1;
}

// 모듈 스코프 객체 — VU 가 동시에 increment 해도 JS single-thread 라 안전.
// 단 k6 는 VU 마다 별도 JS context 라 이 객체는 VU 별로 분리. handleSummary 는 main VU 에서 실행돼
// VU 별 카운트는 손실됨 → 보조용. 정확한 분배는 별도 데이터 dump 필요.
const INSTANCE_HITS_MAP = {};

// VU 간 공유되는 사용자 풀
const users = new SharedArray('users', function () {
    const arr = [];
    for (let i = 1; i <= USER_COUNT; i++) {
        arr.push({ email: `customer${i}@qa.test`, password: 'password' });
    }
    return arr;
});

export const options = {
    scenarios: {
        order_creation: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 50 },   // warm-up
                { duration: '1m',  target: 200 },  // ramp-up
                { duration: '3m',  target: 200 },  // steady state (측정 본 구간)
                { duration: '30s', target: 0 },    // cool-down
            ],
            gracefulRampDown: '10s',
        },
    },
    thresholds: {
        // 측정 본 구간 (steady) 데이터만 집계되도록 tag 로 필터
        'http_req_duration{name:order_create}': ['p(99)<1500'],
        'http_req_failed{name:order_create}': ['rate<0.01'],
    },
    tags: {
        experiment: EXPERIMENT_LABEL,
    },
};

// JWT 토큰 캐시 (VU 별)
const tokenCache = {};

function login(user) {
    if (tokenCache[user.email]) return tokenCache[user.email];

    const res = http.post(
        `${GATEWAY_URL}/user/customer/login`,
        JSON.stringify({ email: user.email, password: user.password }),
        { headers: { 'Content-Type': 'application/json' }, tags: { name: 'login' } }
    );

    if (res.status !== 200) {
        console.error(`Login failed for ${user.email}: ${res.status} ${res.body}`);
        return null;
    }
    const token = res.body.replace(/^"|"$/g, '');  // JSON string 감싸기 제거
    tokenCache[user.email] = token;
    return token;
}

// 시드 SQL 의 product/item 이름 규칙 (qa/seed/load/order.sql 와 일치해야 refreshCart 가 메시지 추가하지 않음)
function pad3(n) { return ('000' + n).slice(-3); }
function productName(productId) { return `QaProduct${pad3(productId)}`; }
function productDescription(productId) { return `Product description ${productId}`; }
function itemName(productId, itemIndex) { return `${productName(productId)}-Item${itemIndex}`; }

function addToCart(token, productId, productItemId, itemIndex, price) {
    // AddProductCartForm 구조: id (productId), sellerId, name, description, productItemList[]
    const body = {
        id: productId,
        sellerId: 1,
        name: productName(productId),
        description: productDescription(productId),
        productItemList: [{
            id: productItemId,
            name: itemName(productId, itemIndex),
            count: 1,
            price: price,
        }],
    };
    return http.post(`${GATEWAY_URL}/order/customer/cart`, JSON.stringify(body), {
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${token}`,
        },
        tags: { name: 'cart_add' },
    });
}

function placeOrder(token, productId, productItemId, itemIndex, price, idempotencyKey) {
    // Cart 구조: messages, productList. messages=[] 로 명시하지 않으면 NPE.
    const body = {
        messages: [],
        productList: [{
            id: productId,
            sellerId: 1,
            name: productName(productId),
            description: productDescription(productId),
            productItemList: [{
                id: productItemId,
                name: itemName(productId, itemIndex),
                count: 1,
                price: price,
            }],
        }],
    };
    return http.post(`${GATEWAY_URL}/order/customer/cart/order`, JSON.stringify(body), {
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${token}`,
            'Idempotency-Key': idempotencyKey,
        },
        tags: { name: 'order_create' },
    });
}

export default function () {
    // 1. 사용자 선택 + 로그인 (캐시됨)
    const user = users[Math.floor(Math.random() * users.length)];
    const token = login(user);
    if (!token) {
        sleep(1);
        return;
    }

    // 2. 상품 선택 (시드된 product_item 중 랜덤)
    const productItemId = PRODUCT_ITEM_BASE_ID + Math.floor(Math.random() * 500);
    const productId = Math.floor((productItemId - 1) / 5) + 1;
    const itemIndex = ((productItemId - 1) % 5) + 1;
    const price = 1000 * itemIndex;

    // 3. 카트 추가 (측정 외 — tags 로 별도 집계)
    const cartRes = addToCart(token, productId, productItemId, itemIndex, price);
    if (cartRes.status !== 200) {
        sleep(0.5);
        return;
    }

    // 4. 주문 (측정 대상)
    const idempotencyKey = uuidv4();
    const orderRes = placeOrder(token, productId, productItemId, itemIndex, price, idempotencyKey);

    check(orderRes, {
        'order status is 200': (r) => r.status === 200,
        'order has X-Instance-Id': (r) => !!r.headers['X-Instance-Id'],
    });

    // 5. 인스턴스 ID 집계 (분배 균등성 검증용)
    // 헤더 이름 normalize (k6 는 lower-case 로 정규화함)
    const instanceId = orderRes.headers['X-Instance-Id']
                    || orderRes.headers['x-instance-id']
                    || 'unknown';
    instanceHits.add(1, { instance: instanceId });
    orderLatency.add(orderRes.timings.duration, { instance: instanceId });
    recordInstanceHit(instanceId);

    // 6. 5% 확률로 같은 Idempotency-Key 재시도 (멱등성 검증)
    if (Math.random() < 0.05) {
        idempotencyReplays.add(1);
        const replayRes = placeOrder(token, productId, productItemId, itemIndex, price, idempotencyKey);
        // 정상 동작 시 같은 응답 (cached) 또는 409 — 새 주문 생성되면 멱등성 위반
        if (replayRes.status === 200 && replayRes.body !== orderRes.body) {
            // 응답이 다른데 둘 다 200 이면 의심스러움 (orderId 가 다르면 위반)
            duplicateOrderResponses.add(1);
        }
    }

    sleep(0.1);  // 살짝의 think time
}

// 부하 종료 후 인스턴스별 분배 summary 출력
export function handleSummary(data) {
    const summary = {
        experiment: EXPERIMENT_LABEL,
        timestamp: new Date().toISOString(),
        order_create: {
            count: data.metrics['http_reqs'] ? data.metrics['http_reqs'].values.count : 0,
            duration_p50: data.metrics['http_req_duration{name:order_create}'] ?
                data.metrics['http_req_duration{name:order_create}'].values['p(50)'] : null,
            duration_p95: data.metrics['http_req_duration{name:order_create}'] ?
                data.metrics['http_req_duration{name:order_create}'].values['p(95)'] : null,
            duration_p99: data.metrics['http_req_duration{name:order_create}'] ?
                data.metrics['http_req_duration{name:order_create}'].values['p(99)'] : null,
            failed_rate: data.metrics['http_req_failed{name:order_create}'] ?
                data.metrics['http_req_failed{name:order_create}'].values.rate : null,
        },
        idempotency: {
            replays_attempted: data.metrics['idempotency_replay_attempts'] ?
                data.metrics['idempotency_replay_attempts'].values.count : 0,
            violations: data.metrics['duplicate_order_responses'] ?
                data.metrics['duplicate_order_responses'].values.count : 0,
        },
        full_metrics: data.metrics,
    };

    // ADR-005 LB 검증: 본 VU 의 INSTANCE_HITS_MAP 만 dump (전체 VU 합산은 k6 한계로 불가, 추세 확인용)
    summary.instance_hits_partial = INSTANCE_HITS_MAP;

    return {
        [`/results/${EXPERIMENT_LABEL}-summary.json`]: JSON.stringify(summary, null, 2),
        stdout: textSummary(data),
    };
}

function textSummary(data) {
    const lines = [];
    lines.push(`\n=== ADR-005 시나리오 3 측정 결과 (${EXPERIMENT_LABEL}) ===\n`);

    const fmt = (v) => (typeof v === 'number') ? v.toFixed(1) : 'n/a';
    const fmtPct = (v) => (typeof v === 'number') ? (v * 100).toFixed(2) + '%' : 'n/a';

    const orderDur = data.metrics['http_req_duration{name:order_create}'];
    if (orderDur && orderDur.values) {
        lines.push(`order_create p50:  ${fmt(orderDur.values['p(50)'])} ms`);
        lines.push(`order_create p95:  ${fmt(orderDur.values['p(95)'])} ms`);
        lines.push(`order_create p99:  ${fmt(orderDur.values['p(99)'])} ms`);
    }
    const orderFail = data.metrics['http_req_failed{name:order_create}'];
    if (orderFail && orderFail.values) {
        lines.push(`order_create fail rate: ${fmtPct(orderFail.values.rate)}`);
    }
    const reqs = data.metrics['http_reqs'];
    if (reqs && reqs.values) {
        lines.push(`총 요청: ${reqs.values.count}, throughput: ${fmt(reqs.values.rate)} req/s`);
    }
    const violations = data.metrics['duplicate_order_responses'];
    if (violations && violations.values) {
        lines.push(`멱등성 위반: ${violations.values.count}`);
    }
    lines.push('');

    return lines.join('\n');
}
