// =============================================================================
// ADR-005 시나리오 3 — orderApi Load Balancing 측정 (realistic cart_add + order 버스트)
//
// 목적: orderApi 인스턴스 1/2/4 스케일 시 Gateway+Eureka LoadBalancer 효과를
//      "현실적 장바구니→주문" 플로우로 대량(기본 10만+) 버스트하여 정량 측정.
//
// 설계 — 측정 오염 요소를 setup 으로 분리해 orderApi 가 병목이 되게 한다:
//   - login: setup 에서 미리 (측정 루프에 로그인 churn 이 없어야 userApi 가 아니라
//     orderApi 가 병목이 됨). 충전은 시드 balance=10_000_000 로 대체.
//   - 측정 루프: cart_add(POST /order/customer/cart) → order(POST .../cart/order).
//     order 는 Redis 카트를 필수로 요구하고 주문 시 카트를 "차감"하므로,
//     cart_add(+1) → order(-1) 쌍이면 카트가 누적되지 않는다 (OrderService.order).
//   - VU:유저 1:1 매핑 (VUS ≤ USER_COUNT) — 같은 고객 카트에 동시 접근(race) 방지.
//   - 상품은 1..PRODUCT_ITEM_COUNT 중 랜덤 — 단일 product_item 낙관적 락(ADR-002)
//     경합을 피하고 부하를 분산 (orderApi 가 병목이 되도록).
//   - shared-iterations 로 ORDER_COUNT 건을 고VU 로 "거의 동시에" 버스트.
//
// 전제 (seed/user.sql → seed/order.sql 순서로 시드 완료):
//   - userApi DB: seller1(id=1) + customer{1..N}@qa.test, pw "password", verify=true, balance=10M
//   - orderApi DB: 100 product × 5 product_item (id 1..500, seller_id=1, 재고 1M)
//
// 환경변수:
//   GATEWAY_URL (기본 http://gateway)
//   VUS (기본 500)            — 동시 VU = 버스트 강도. USER_COUNT 이하로 유지 (카트 race 방지)
//   USER_COUNT (기본 = VUS)   — setup 에서 로그인할 유저 수 (시드 customer 수 이하)
//   ORDER_COUNT (기본 100000) — 총 주문(=iteration) 수
//   PRODUCT_ITEM_COUNT (기본 500)
//   MAX_DURATION (기본 30m)
//   EXPERIMENT_LABEL (예: E1/E2/E3)
// =============================================================================

import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const GATEWAY_URL = __ENV.GATEWAY_URL || 'http://gateway';
const VUS = parseInt(__ENV.VUS || '500');
const USER_COUNT = parseInt(__ENV.USER_COUNT || String(VUS));
const ORDER_COUNT = parseInt(__ENV.ORDER_COUNT || '100000');
const PRODUCT_ITEM_COUNT = parseInt(__ENV.PRODUCT_ITEM_COUNT || '500');
const EXPERIMENT_LABEL = __ENV.EXPERIMENT_LABEL || 'unknown';

const orderLatency = new Trend('order_create_latency', true);
const cartLatency = new Trend('cart_add_latency', true);
const instanceHits = new Counter('instance_hits');
const idempotencyReplays = new Counter('idempotency_replay_attempts');
const duplicateOrderResponses = new Counter('duplicate_order_responses');

export const options = {
    scenarios: {
        cart_order_burst: {
            executor: 'shared-iterations',
            vus: VUS,
            iterations: ORDER_COUNT,
            maxDuration: __ENV.MAX_DURATION || '30m',
        },
    },
    setupTimeout: '10m',
    thresholds: {
        'http_req_duration{name:order_create}': ['p(95)<1000', 'p(99)<2000'],
        'http_req_failed{name:order_create}': ['rate<0.01'],
        'http_req_failed{name:cart_add}': ['rate<0.01'],
    },
    tags: { experiment: EXPERIMENT_LABEL },
};

// 상품/아이템 이름 규칙 — seed/order.sql 의 QaProduct{nnn}-Item{k} 와 일치해야 검증 통과
function pad3(n) { return ('000' + n).slice(-3); }
function productName(pid) { return `QaProduct${pad3(pid)}`; }
function productDescription(pid) { return `Product description ${pid}`; }
function itemName(pid, k) { return `${productName(pid)}-Item${k}`; }

// ── setup: USER_COUNT 명 login (충전은 시드 balance 로 대체) ──
export function setup() {
    const users = [];
    for (let i = 1; i <= USER_COUNT; i++) {
        const email = `customer${i}@qa.test`;
        const res = http.post(
            `${GATEWAY_URL}/user/customer/login`,
            JSON.stringify({ email, password: 'password' }),
            { headers: { 'Content-Type': 'application/json' } }
        );
        if (res.status !== 200) {
            console.error(`[setup] login fail ${email}: ${res.status}`);
            continue;
        }
        users.push({ email, token: res.body.replace(/^"|"$/g, '') });
    }
    console.log(`[setup] ${users.length}/${USER_COUNT} 로그인 완료 (VUS=${VUS}, ORDER_COUNT=${ORDER_COUNT})`);
    if (users.length === 0) throw new Error('[setup] 로그인 0건 — 시드/게이트웨이 확인');
    return { users };
}

// ── default: cart_add → order (VU:유저 1:1, 상품 랜덤) ──
export default function (data) {
    const user = data.users[(__VU - 1) % data.users.length];
    if (!user) return;
    const auth = { 'Content-Type': 'application/json', 'Authorization': `Bearer ${user.token}` };

    // 랜덤 상품 (product_item id 1..PRODUCT_ITEM_COUNT) → product/item 산술 매핑 (seed 와 일치)
    const productItemId = 1 + Math.floor(Math.random() * PRODUCT_ITEM_COUNT);
    const productId = Math.floor((productItemId - 1) / 5) + 1;
    const itemIndex = ((productItemId - 1) % 5) + 1;
    const price = 1000 * itemIndex;

    const productPayload = {
        id: productId,
        sellerId: 1,
        name: productName(productId),
        description: productDescription(productId),
        productItemList: [{ id: productItemId, name: itemName(productId, itemIndex), count: 1, price: price }],
    };

    // 1) cart_add (order 가 Redis 카트를 필수로 요구하므로 매 주문 직전 담는다)
    const cartRes = http.post(`${GATEWAY_URL}/order/customer/cart`, JSON.stringify(productPayload),
        { headers: auth, tags: { name: 'cart_add' } });
    cartLatency.add(cartRes.timings.duration);
    if (cartRes.status !== 200) return;   // 카트 실패 시 주문 스킵

    // 2) order (측정 대상)
    const idemKey = uuidv4();
    const orderBody = { messages: [], productList: [productPayload] };
    const orderRes = http.post(`${GATEWAY_URL}/order/customer/cart/order`, JSON.stringify(orderBody),
        { headers: { ...auth, 'Idempotency-Key': idemKey }, tags: { name: 'order_create' } });

    check(orderRes, {
        'order 200': (r) => r.status === 200,
        'has X-Instance-Id': (r) => !!(r.headers['X-Instance-Id'] || r.headers['x-instance-id']),
    });
    const instanceId = orderRes.headers['X-Instance-Id'] || orderRes.headers['x-instance-id'] || 'unknown';
    instanceHits.add(1, { instance: instanceId });
    orderLatency.add(orderRes.timings.duration, { instance: instanceId });

    // 5% 확률로 같은 Idempotency-Key 재전송 (ADR-001 멱등성 검증)
    if (Math.random() < 0.05) {
        idempotencyReplays.add(1);
        const replay = http.post(`${GATEWAY_URL}/order/customer/cart/order`, JSON.stringify(orderBody),
            { headers: { ...auth, 'Idempotency-Key': idemKey }, tags: { name: 'order_replay' } });
        if (replay.status === 200 && replay.body !== orderRes.body) duplicateOrderResponses.add(1);
    }
    // think time 없음 — "거의 동시" 버스트 유지
}

export function handleSummary(data) {
    const m = data.metrics;
    const g = (k, f) => (m[k] && m[k].values && m[k].values[f] != null ? m[k].values[f] : null);
    const f1 = (v) => (typeof v === 'number' ? v.toFixed(1) : 'n/a');
    const summary = {
        experiment: EXPERIMENT_LABEL,
        order_count: g('http_reqs{name:order_create}', 'count'),
        order_throughput_rps: g('http_reqs{name:order_create}', 'rate'),
        order_p50: g('http_req_duration{name:order_create}', 'med'),
        order_p95: g('http_req_duration{name:order_create}', 'p(95)'),
        order_p99: g('http_req_duration{name:order_create}', 'p(99)'),
        order_fail_rate: g('http_req_failed{name:order_create}', 'rate'),
        cart_add_p95: g('http_req_duration{name:cart_add}', 'p(95)'),
        idempotency_replays: g('idempotency_replay_attempts', 'count'),
        idempotency_violations: g('duplicate_order_responses', 'count'),
        full_metrics: m,
    };
    const fr = summary.order_fail_rate;
    const stdout = `\n=== orderApi LB (${EXPERIMENT_LABEL}) ===\n`
        + `orders: ${summary.order_count}, order throughput: ${f1(summary.order_throughput_rps)} req/s\n`
        + `order p50/p95/p99: ${f1(summary.order_p50)} / ${f1(summary.order_p95)} / ${f1(summary.order_p99)} ms\n`
        + `cart_add p95: ${f1(summary.cart_add_p95)} ms\n`
        + `order fail: ${typeof fr === 'number' ? (fr * 100).toFixed(2) + '%' : 'n/a'}, `
        + `idempotency violations: ${summary.idempotency_violations}\n`;
    return {
        [`/results/${EXPERIMENT_LABEL}-summary.json`]: JSON.stringify(summary, null, 2),
        stdout,
    };
}
