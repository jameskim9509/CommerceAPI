// =============================================================================
// ADR-008 주문 정합성 통합 시나리오 부하 (constant-arrival-rate)
//
// "작은 고객 풀이 반복 주문해 만드는 대규모 주문에 한정 재고 hot SKU 경합·멱등 재전송을 섞어
//  원하는 장애 5종을 한 부하에서 동시에 발화시킨다."
//
// 트래픽 구성(ADR-008 §통합 시나리오 구성):
//   normal 90% | hot(한정재고 경합) 8% | replay(멱등 재전송 overlay) 2%
//
// ★ 처리량 고정: constant-arrival-rate + pre-alloc VU (ADR-008 §재현 하네스 1 — ramping-vus 금지).
//   워밍업/카오스 타이밍은 k6 밖에서: chaos-schedule.sh 가 CONFIRMED 진행도에 앵커해 T1–T4 를 주입.
//
// ★ 최종 정합성(CONFIRMED/FAILED, 돈 보존, 초과판매)은 k6 가 판정하지 않는다 — 결제·재고·환불은 전부
//   비동기(SAGA)라 k6 응답은 200/PENDING 이다. 부하 종료 → 정착(quiescence) → verify-consistency.sh 가 판정.
//   k6 가 직접 관측하는 유일한 원하는 장애는 ① 멱등성: duplicate_order_responses.
//
// 시드 일치(중요): CartService.refreshCart 가 product.name/description·item.name/price 를 DB 와 비교하므로
//   아래 이름/설명/가격은 seed/order.sql 과 "정확히" 같아야 한다 (다르면 CART_CHECK_REQUIRED).
//
// 환경변수: GATEWAY_URL, ORDER_TARGET(총 주문 수), ARRIVAL_RATE(초당 도착), RICH_POOL, RUN_LABEL
// =============================================================================

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const GATEWAY_URL  = __ENV.GATEWAY_URL || 'http://gateway';
const ORDER_TARGET = parseInt(__ENV.ORDER_TARGET || '100000');
const ARRIVAL_RATE = parseInt(__ENV.ARRIVAL_RATE || '200');   // iterations/s
const RICH_POOL    = parseInt(__ENV.RICH_POOL || '300');
const RUN_LABEL    = __ENV.RUN_LABEL || 'unknown';

// 총 주문 수 ÷ 초당 도착 = 부하 지속(초). 최소 30s 보장.
const DURATION_S = Math.max(30, Math.ceil(ORDER_TARGET / ARRIVAL_RATE));

// ---- 커스텀 메트릭 ----
const orderLatency  = new Trend('order_create_latency', true);
const attempts      = new Counter('order_attempts');
const attemptsHot   = new Counter('order_attempts_hot');
const attemptsNorm  = new Counter('order_attempts_normal');
const order2xx      = new Counter('order_2xx');
const orderNon2xx   = new Counter('order_non2xx');
const cartFail      = new Counter('cart_add_fail');
const idemReplays   = new Counter('idempotency_replay_attempts');
const dupOrders     = new Counter('duplicate_order_responses');   // 원하는 장애 ① 위반 카운터

export const options = {
    scenarios: {
        consistency: {
            executor: 'constant-arrival-rate',
            rate: ARRIVAL_RATE,
            timeUnit: '1s',
            duration: `${DURATION_S}s`,
            preAllocatedVUs: parseInt(__ENV.PRE_VUS || '300'),
            maxVUs: parseInt(__ENV.MAX_VUS || '800'),
        },
    },
    thresholds: {
        // 측정 임계값은 아니고(카오스로 실패가 의도됨), 참고용 latency 트렌드만.
        'order_create_latency': ['p(99)>=0'],
    },
    tags: { run: RUN_LABEL },
};

// VU 별 토큰 캐시
const tokenCache = {};

function pad3(n) { return ('000' + n).slice(-3); }

// seed/order.sql 와 일치해야 하는 이름 규칙 (전부 ASCII)
function hotSku() {
    return { productId: 10001, itemId: 10001, sellerId: 1, price: 1000,
             productName: 'CT-HOT-Limited', productDesc: 'CT hot limited SKU', itemName: 'CT-HOT-Limited-Item' };
}
function normalSku() {
    const p = 1 + Math.floor(Math.random() * 50);   // 1..50
    const k = 1 + Math.floor(Math.random() * 5);    // 1..5
    const productName = `CtNormal${pad3(p)}`;
    return { productId: 20000 + p, itemId: 20000 + (p - 1) * 5 + k, sellerId: 1, price: 1000 * k,
             productName, productDesc: `CT normal product ${p}`, itemName: `${productName}-Item${k}` };
}

function login(email) {
    if (tokenCache[email]) return tokenCache[email];
    const res = http.post(`${GATEWAY_URL}/user/customer/login`,
        JSON.stringify({ email, password: 'password' }),
        { headers: { 'Content-Type': 'application/json' }, tags: { name: 'login' } });
    if (res.status !== 200) return null;
    const token = res.body.replace(/^"|"$/g, '');
    tokenCache[email] = token;
    return token;
}

function addToCart(token, sku) {
    const body = {
        id: sku.productId, sellerId: sku.sellerId, name: sku.productName, description: sku.productDesc,
        productItemList: [{ id: sku.itemId, name: sku.itemName, count: 1, price: sku.price }],
    };
    return http.post(`${GATEWAY_URL}/order/customer/cart`, JSON.stringify(body), {
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
        tags: { name: 'cart_add' },
    });
}

function placeOrder(token, sku, idempotencyKey) {
    const body = {
        messages: [],
        productList: [{
            id: sku.productId, sellerId: sku.sellerId, name: sku.productName, description: sku.productDesc,
            productItemList: [{ id: sku.itemId, name: sku.itemName, count: 1, price: sku.price }],
        }],
    };
    return http.post(`${GATEWAY_URL}/order/customer/cart/order`, JSON.stringify(body), {
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}`, 'Idempotency-Key': idempotencyKey },
        tags: { name: 'order_create' },
    });
}

// 응답 body 에서 orderId 추출 (OrderDto.id). 실패 시 raw body 로 fallback.
function orderIdOf(res) {
    try { const o = JSON.parse(res.body); return (o && o.id != null) ? String(o.id) : res.body; }
    catch (e) { return res.body; }
}

export default function () {
    // 1) 트래픽 유형 결정: hot 8% / replay 2% / normal 90%
    const roll = Math.random();
    let type;
    if (roll < 0.08) type = 'hot';
    else if (roll < 0.10) type = 'replay';
    else type = 'normal';

    // 2) 고객 + SKU 선택
    const email = `ctrich${1 + Math.floor(Math.random() * RICH_POOL)}@qa.test`;
    const sku = (type === 'hot') ? hotSku() : normalSku();

    const token = login(email);
    if (!token) { sleep(0.2); return; }

    // 3) 카트 적재 (hot SKU 소진 후엔 NOT_ENOUGH_ITEM_COUNT 로 실패할 수 있음 — 정상 backpressure)
    const cartRes = addToCart(token, sku);
    if (cartRes.status !== 200) { cartFail.add(1); sleep(0.1); return; }

    // 4) 주문
    attempts.add(1);
    if (type === 'hot') attemptsHot.add(1);
    else attemptsNorm.add(1);

    const key = uuidv4();
    const res = placeOrder(token, sku, key);
    orderLatency.add(res.timings.duration);
    if (res.status === 200) order2xx.add(1); else orderNon2xx.add(1);
    check(res, { 'order accepted (200)': (r) => r.status === 200 });

    // 5) 멱등 재전송 overlay (원하는 장애 ①): 같은 키로 즉시 재전송 → orderId 가 다르면 위반
    if (type === 'replay' && res.status === 200) {
        idemReplays.add(1);
        const replay = placeOrder(token, sku, key);
        if (replay.status === 200 && orderIdOf(replay) !== orderIdOf(res)) {
            dupOrders.add(1);   // 멱등성 위반: 같은 키가 새 orderId 를 만들었다
        }
    }
}

export function handleSummary(data) {
    const val = (m) => (data.metrics[m] ? data.metrics[m].values.count : 0);
    const summary = {
        run: RUN_LABEL,
        params: { order_target: ORDER_TARGET, arrival_rate: ARRIVAL_RATE, duration_s: DURATION_S, rich_pool: RICH_POOL },
        attempts: { total: val('order_attempts'), hot: val('order_attempts_hot'), normal: val('order_attempts_normal') },
        order_http: { ok_2xx: val('order_2xx'), non_2xx: val('order_non2xx'), cart_add_fail: val('cart_add_fail') },
        idempotency: { replays_attempted: val('idempotency_replay_attempts'), duplicate_order_responses: val('duplicate_order_responses') },
        order_create_latency_ms: data.metrics['order_create_latency'] ? data.metrics['order_create_latency'].values : null,
    };
    return {
        [`/results/${RUN_LABEL}-k6-summary.json`]: JSON.stringify(summary, null, 2),
        stdout: `\n=== ADR-008 정합성 부하 (${RUN_LABEL}) ===\n` +
                `attempts total=${summary.attempts.total} (hot=${summary.attempts.hot} normal=${summary.attempts.normal})\n` +
                `order 2xx=${summary.order_http.ok_2xx} non2xx=${summary.order_http.non_2xx} cart_fail=${summary.order_http.cart_add_fail}\n` +
                `idempotency: replays=${summary.idempotency.replays_attempted} duplicate_order_responses=${summary.idempotency.duplicate_order_responses}\n`,
    };
}
