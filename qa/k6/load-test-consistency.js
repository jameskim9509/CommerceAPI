// =============================================================================
// 정합성 통합 시나리오 — 멱등성 + 낙관적 락(초과판매) + SAGA 보상 을 한 부하에서 모두 발화
//
// 시나리오:
//   "재고 1,200개로 한정된 단일 인기 상품을, 고객 풀(결제가능 ctrich 150 + 잔액부족 ctbroke 30)에서
//    20명이 동시에 1개씩 반복 주문하고, 그중 5%는 같은 Idempotency-Key 로 재전송(더블클릭)하는
//    고동시성 주문 폭주 시나리오"
//
// 각 메커니즘 발화 지점:
//   ① 멱등성 : 5% 가 같은 키로 재전송 → 두 응답의 orderId 가 같아야 함 (다르면 duplicate_orders 위반)
//   ② 낙관적 락 : 1,200 한정 재고에 동시 차감 경합 → 초과판매 0 (재고 음수 0, CONFIRMED==차감량)
//   ③ SAGA 보상 : 재고소진/락충돌(ctrich)·잔액부족(ctbroke) 실패분 → 전액 환불 + FAILED
//
// 전제: qa/seed/scenario/{user,order}.sql 주입 완료.
// 인프라: orderApi 4 인스턴스 + Kafka 4 파티션(재고 차감 병렬화 → 락 충돌 유발).
// =============================================================================

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const GATEWAY_URL = __ENV.GATEWAY_URL || 'http://gateway';
const RICH_POOL   = parseInt(__ENV.RICH_POOL  || '150');   // ctrich1..150 (seed 300 중 일부 사용)
const BROKE_POOL  = parseInt(__ENV.BROKE_POOL || '30');    // ctbroke1..30 (seed 60 중 일부)
const BROKE_RATE  = parseFloat(__ENV.BROKE_RATE || '0.15');// 결제 실패 유발 비율
const REPLAY_RATE = parseFloat(__ENV.REPLAY_RATE || '0.05');// 더블클릭(멱등) 재전송 비율
const PER_USER_CART = parseInt(__ENV.PER_USER_CART || '100000');
const LABEL = __ENV.EXPERIMENT_LABEL || 'CONSIST';

// 한정 SKU (seed 와 정확히 일치해야 함)
const HOT_PRODUCT_ID = 10001;
const HOT_ITEM_ID    = 10001;
const HOT_PRICE      = 1000;
const PRODUCT_NAME   = 'CT-HOT-Limited';
const PRODUCT_DESC   = 'CT-HOT limited stock contention';
const ITEM_NAME      = 'CT-HOT-Limited-Item';

const idempotencyReplays   = new Counter('idempotency_replay_attempts');
const duplicateOrders      = new Counter('duplicate_order_responses'); // ① 멱등성 위반
const orderAccepted        = new Counter('order_accepted');
const orderRejectedSync    = new Counter('order_rejected_sync');
const cartRejected         = new Counter('cart_rejected');   // 재고 소진으로 카트 담기 거절(정상)

export const options = {
    scenarios: {
        contention: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '15s', target: 20 },
                { duration: '90s', target: 20 },   // steady — 본 부하
                { duration: '15s', target: 0 },
            ],
            gracefulRampDown: '10s',
        },
    },
    tags: { experiment: LABEL },
};

function login(email) {
    const res = http.post(`${GATEWAY_URL}/user/customer/login`,
        JSON.stringify({ email, password: 'password' }),
        { headers: { 'Content-Type': 'application/json' } });
    if (res.status !== 200) {
        console.error(`[setup] login fail ${email}: ${res.status} ${res.body}`);
        return null;
    }
    return res.body.replace(/^"|"$/g, '');
}

function addHotToCart(token, count) {
    const body = {
        id: HOT_PRODUCT_ID, sellerId: 1, name: PRODUCT_NAME, description: PRODUCT_DESC,
        productItemList: [{ id: HOT_ITEM_ID, name: ITEM_NAME, count, price: HOT_PRICE }],
    };
    return http.post(`${GATEWAY_URL}/order/customer/cart`, JSON.stringify(body),
        { headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` } });
}

function placeHotOrder(token, idempotencyKey) {
    const body = {
        messages: [],
        productList: [{
            id: HOT_PRODUCT_ID, sellerId: 1, name: PRODUCT_NAME, description: PRODUCT_DESC,
            productItemList: [{ id: HOT_ITEM_ID, name: ITEM_NAME, count: 1, price: HOT_PRICE }],
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

// ── setup: 고객 풀 로그인만 (카트 사전 적재 X) ──
//   CartService.validateAddForm 은 카트가 비었을 때(첫 담기) 수량 검증을 건너뛰므로
//   큰 수량을 미리 담으면 이후 refreshCart 가 재고 초과로 CART_CHECK_REQUIRED 를 던진다.
//   → 매 반복 count=1 로 담고(재고로 게이팅) 즉시 주문하는 패턴 사용.
export function setup() {
    const rich = [], broke = [];
    for (let i = 1; i <= RICH_POOL; i++) { const t = login(`ctrich${i}@qa.test`); if (t) rich.push(t); }
    for (let i = 1; i <= BROKE_POOL; i++) { const t = login(`ctbroke${i}@qa.test`); if (t) broke.push(t); }
    console.log(`[setup] logged in: rich=${rich.length}, broke=${broke.length}`);
    return { rich, broke };
}

export default function (data) {
    const useBroke = data.broke.length > 0 && Math.random() < BROKE_RATE;
    const pool = useBroke ? data.broke : data.rich;
    if (!pool.length) { sleep(1); return; }
    const token = pool[Math.floor(Math.random() * pool.length)];

    // 1) 카트에 1개 담기 — 재고로 게이팅됨(재고 0 이면 400 NOT_ENOUGH_ITEM_COUNT = 정상 소진)
    const cart = addHotToCart(token, 1);
    if (cart.status !== 200) { cartRejected.add(1); sleep(0.3); return; }

    // 2) 주문 (count=1)
    const key = uuidv4();
    const res = placeHotOrder(token, key);
    const ok = check(res, { 'order accepted (200)': (r) => r.status === 200 });
    if (ok) orderAccepted.add(1); else orderRejectedSync.add(1);

    // ① 멱등성: 5% 확률로 같은 키 재전송 (더블클릭) → orderId 동일해야 정상
    if (res.status === 200 && Math.random() < REPLAY_RATE) {
        idempotencyReplays.add(1);
        const replay = placeHotOrder(token, key);
        if (replay.status === 200) {
            const oid1 = res.json('orderId');
            const oid2 = replay.json('orderId');
            if (oid1 !== oid2) duplicateOrders.add(1);  // 새 주문 생성 = 멱등성 위반
        }
    }

    sleep(0.3);
}

export function handleSummary(data) {
    const m = data.metrics;
    const val = (k, f) => (m[k] && m[k].values && m[k].values[f] !== undefined) ? m[k].values[f] : 0;
    const summary = {
        experiment: LABEL,
        order_accepted: val('order_accepted', 'count'),
        order_rejected_sync: val('order_rejected_sync', 'count'),
        cart_rejected_stock: val('cart_rejected', 'count'),
        idempotency_replay_attempts: val('idempotency_replay_attempts', 'count'),
        duplicate_order_responses: val('duplicate_order_responses', 'count'),
        http_reqs: val('http_reqs', 'count'),
        throughput_rps: val('http_reqs', 'rate'),
    };
    return {
        [`/results/${LABEL}-summary.json`]: JSON.stringify(summary, null, 2),
        stdout: '\n=== 정합성 시나리오 (' + LABEL + ') ===\n' +
            `주문 수락(200): ${summary.order_accepted}\n` +
            `동기 거절(4xx/5xx): ${summary.order_rejected_sync}\n` +
            `멱등 재전송 시도: ${summary.idempotency_replay_attempts}\n` +
            `멱등성 위반(중복 주문): ${summary.duplicate_order_responses}\n` +
            `throughput: ${summary.throughput_rps.toFixed(1)} req/s\n`,
    };
}
