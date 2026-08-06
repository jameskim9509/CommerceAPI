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
//
// 시드 일치(중요): CartService.refreshCart 가 product.name/description·item.name/price 를 DB 와 비교하므로
//   아래 이름/설명/가격은 seed/order.sql 과 "정확히" 같아야 한다 (다르면 CART_CHECK_REQUIRED).
//
// 환경변수: GATEWAY_URL, ORDER_TARGET(총 주문 수), ARRIVAL_RATE(초당 도착), RICH_POOL, RUN_LABEL
//
// summary(JSON) 에는 커스텀 카운터 + k6 기본 지표를 함께 남긴다 (아래 handleSummary):
//   dropped_iterations > 0 → 도착률 미달(VU 부족)로 "처리량 고정" 가정이 깨진 런
//   http_req_duration{name:order_create|cart_add|login} → 엔드포인트별 p99 분해
//   replay_non2xx → 멱등 재전송이 200 이 아니어서 ① 판정이 성립하지 않은 건수
// =============================================================================

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import exec from 'k6/execution';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const GATEWAY_URL  = __ENV.GATEWAY_URL || 'http://gateway';
const ORDER_TARGET = parseInt(__ENV.ORDER_TARGET || '100000');
const ARRIVAL_RATE = parseInt(__ENV.ARRIVAL_RATE || '200');   // iterations/s
const RICH_POOL    = parseInt(__ENV.RICH_POOL || '300');
const RUN_LABEL    = __ENV.RUN_LABEL || 'unknown';
// ★ 클라이언트 타임아웃. mysql 이 멎어도 앱은 즉시 실패하지 않는다 — HikariCP 가 커넥션 획득을
//   connectionTimeout(기본 30초)까지 블로킹한다
const REQ_TIMEOUT  = __ENV.REQ_TIMEOUT || '10s';
// 타임아웃 시 같은 키로 1회 재시도할지. 런마다 summary 에 기록되므로 사후에 어느 모드였는지 알 수 있다.
const RETRY_ON_TIMEOUT = (__ENV.RETRY_ON_TIMEOUT || '1') !== '0';

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
const replay2xx     = new Counter('idempotency_replay_2xx');      // 재전송이 200 (방어 ON 이면 캐시된 응답)
const replayNon2xx  = new Counter('idempotency_replay_non2xx');
const tokenMissing  = new Counter('setup_token_missing');         // setup 로그인 실패로 건너뛴 이터레이션

// ---- 타임아웃 재시도 ----
const timeoutRetries   = new Counter('timeout_retry_attempts');
const timeoutRetry2xx  = new Counter('timeout_retry_2xx');      // 재시도가 200 — control 이면 새 주문 생성 의심
const timeoutRetryFail = new Counter('timeout_retry_non2xx');   // 409(방어 ON) 또는 400(카트 소비됨) 등

const thresholds = {
    'order_create_latency': ['p(99)>=0'],
    'http_req_duration{name:order_create}': ['p(99)>=0'],
    'http_req_duration{name:cart_add}': ['p(99)>=0'],
    'http_req_duration{name:login}': ['p(99)>=0'],
    'http_reqs{name:order_create}': ['count>=0'],
    'http_reqs{name:cart_add}': ['count>=0'],
    'http_reqs{name:login}': ['count>=0'],
    'http_req_failed{name:order_create}': ['rate>=0'],
    'http_req_failed{name:cart_add}': ['rate>=0'],
    'http_req_failed{name:login}': ['rate>=0'],
    // setup 의 사전 로그인은 부하 구간 밖이라 name:login 과 분리해 집계한다
    // (섞이면 부하 중 로그인이 있는 것처럼 보인다 — 정상이면 name:login 은 reqs=0).
    'http_reqs{name:login_setup}': ['count>=0'],
    'http_req_duration{name:login_setup}': ['p(99)>=0'],
};

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
    thresholds,
    summaryTrendStats: ['min', 'avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'p(99.9)', 'max'],
    setupTimeout: '180s',            // 사전 로그인(BCrypt) 여유
    tags: { run: RUN_LABEL },
};

function pad3(n) { return ('000' + n).slice(-3); }

// seed/order.sql 와 일치해야 하는 이름 규칙
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

// ★ 로그인은 부하 구간에서 뺀다 (setup 에서 1회 발급 → 모든 VU 가 공유).
//   로그인은 BCrypt 라 순수 CPU 인데 userApi 는 2코어다. 부하 안에 두면 200/s 에서 med 19초까지
//   밀려 이터레이션 전체를 잡아먹고, VU 800 이 전부 로그인 대기에 묶여 도착률을 못 채운다
//   (실측: iterations 20,895 / dropped 79,106). 측정 대상은 주문 정합성이지 인증이 아니다.
//   JWT 유효시간 1시간(JwtTokenProvider.TOKEN_EXPIRE_TIME) > 최장 런(10만/200 = 500초).
export function setup() {
    const tokens = {};
    const BATCH = 50;
    let failed = 0;

    for (let start = 1; start <= RICH_POOL; start += BATCH) {
        const emails = [];
        const reqs = [];
        for (let i = start; i < start + BATCH && i <= RICH_POOL; i++) {
            const email = `ctrich${i}@qa.test`;
            emails.push(email);
            reqs.push(['POST', `${GATEWAY_URL}/user/customer/login`,
                JSON.stringify({ email, password: 'password' }),
                { headers: { 'Content-Type': 'application/json' }, tags: { name: 'login_setup' } }]);
        }
        http.batch(reqs).forEach((res, i) => {
            if (res.status === 200) tokens[emails[i]] = res.body.replace(/^"|"$/g, '');
            else failed++;
        });
    }

    console.log(`[setup] 토큰 발급 ${Object.keys(tokens).length}/${RICH_POOL} (실패 ${failed})`);
    return { tokens };
}

function addToCart(token, sku) {
    const body = {
        id: sku.productId, sellerId: sku.sellerId, name: sku.productName, description: sku.productDesc,
        productItemList: [{ id: sku.itemId, name: sku.itemName, count: 1, price: sku.price }],
    };
    return http.post(`${GATEWAY_URL}/order/customer/cart`, JSON.stringify(body), {
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
        tags: { name: 'cart_add' },
        timeout: REQ_TIMEOUT,
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
        timeout: REQ_TIMEOUT,
    });
}

// 응답 body 에서 orderId 추출 (OrderDto.id). 실패 시 raw body 로 fallback.
function orderIdOf(res) {
    try { const o = JSON.parse(res.body); return (o && o.id != null) ? String(o.id) : res.body; }
    catch (e) { return res.body; }
}

export default function (data) {
    // 1) 트래픽 유형 결정: hot 8% / replay 2% / normal 90%
    const roll = Math.random();
    let type;
    if (roll < 0.08) type = 'hot';
    else if (roll < 0.10) type = 'replay';
    else type = 'normal';

    // 2) 고객 + SKU 선택 — 토큰은 setup 에서 받은 것을 그대로 쓴다 (부하 중 로그인 없음)
    const email = `ctrich${1 + Math.floor(Math.random() * RICH_POOL)}@qa.test`;
    const sku = (type === 'hot') ? hotSku() : normalSku();

    const token = data.tokens[email];
    if (!token) { tokenMissing.add(1); return; }   // setup 에서 발급 실패한 고객

    // 3) 카트 적재 (hot SKU 소진 후엔 NOT_ENOUGH_ITEM_COUNT 로 실패할 수 있음 — 정상 backpressure)
    const cartRes = addToCart(token, sku);
    if (cartRes.status !== 200) { cartFail.add(1); sleep(0.1); return; }

    // 4) 주문
    attempts.add(1);
    if (type === 'hot') attemptsHot.add(1);
    else attemptsNorm.add(1);

    // ★ 멱등키에 '요청 시점의 활성 VU 수'를 인코딩한다 — 동시성 장애(②·④)를 부하 수준과 대조하기 위함.
    //   앱 스키마를 건드리지 않고 계측을 심는 유일한 경로다: 이 문자열이 그대로
    //   orders.idempotency_key(VARCHAR(64)) 에 저장되므로, 위반 주문의 VU 를 사후에 조회할 수 있다.
    //   uuid(36) + '-vu' + 최대 4자리 = 42자 → 컬럼 상한 64 안에 들어간다.
    //
    // ★ ① 측정을 깨지 않는다: 아래 재전송·타임아웃 재시도가 이 key 변수를 '그대로' 재사용하므로
    //   중복 주문은 여전히 동일 문자열을 갖는다(v1 = 같은 키로 만들어진 주문의 초과분).
    //   VU 를 재전송 시점에 다시 읽으면 키가 갈라져 ① 이 통째로 0 이 되므로 절대 그렇게 하지 말 것.
    //
    // ★ 해석 주의: VU 는 원인이 아니라 결과일 수 있다. 요청이 느려지면 k6 가 도착률을 유지하려고
    //   VU 를 늘리므로, 카오스 구간에서 VU 급등과 위반이 함께 나타나는 것은 공통 원인에 의한 상관이다.
    const key = `${uuidv4()}-vu${exec.instance.vusActive}`;
    const res = placeOrder(token, sku, key);
    orderLatency.add(res.timings.duration);
    if (res.status === 200) order2xx.add(1); else orderNon2xx.add(1);
    check(res, { 'order accepted (200)': (r) => r.status === 200 });

    // 5) 타임아웃 재시도 — 같은 Idempotency-Key 로 1회 재전송한다.
    if (RETRY_ON_TIMEOUT && res.status === 0) {
        timeoutRetries.add(1);
        const retry = placeOrder(token, sku, key);
        if (retry.status === 200) timeoutRetry2xx.add(1); else timeoutRetryFail.add(1);
    }

    // 6) 멱등 재전송 overlay (원하는 장애 ①): 같은 키로 즉시 재전송 → orderId 가 다르면 위반
    if (type === 'replay' && res.status === 200) {
        idemReplays.add(1);
        const replay = placeOrder(token, sku, key);
        if (replay.status === 200) replay2xx.add(1); else replayNon2xx.add(1);
    }
}

export function handleSummary(data) {
    const met   = (m) => data.metrics[m];
    const val   = (m) => (met(m) ? met(m).values.count : 0);                       // Counter
    const trend = (m) => (met(m) ? met(m).values : null);                          // Trend (키 = summaryTrendStats)
    const gauge = (m) => (met(m) ? { value: met(m).values.value, max: met(m).values.max } : null);
    const ratio = (m) => {                                                        // Rate
        const x = met(m);
        return x ? { rate: x.values.rate, passes: x.values.passes, fails: x.values.fails } : null;
    };
    // 엔드포인트(태그 name) 별 요청수/실패율/지연. reqs=0 이면 지연은 표본 없음(0 이 아님).
    const endpoint = (name) => ({
        reqs: val(`http_reqs{name:${name}}`),
        failed: ratio(`http_req_failed{name:${name}}`),
        duration_ms: trend(`http_req_duration{name:${name}}`),
    });

    const summary = {
        run: RUN_LABEL,
        params: { order_target: ORDER_TARGET, arrival_rate: ARRIVAL_RATE, duration_s: DURATION_S, rich_pool: RICH_POOL },
        // ---- k6 기본 지표 (HTTP 요청 단위) — 이 런이 측정으로서 성립했는지 먼저 본다 ----
        k6: {
            iterations: val('iterations'),
            dropped_iterations: val('dropped_iterations'),
            vus: gauge('vus'),
            vus_max: gauge('vus_max'),
            http_reqs: val('http_reqs'),
            http_req_failed: ratio('http_req_failed'),
            checks: ratio('checks'),
            data_sent_bytes: val('data_sent'),
            data_received_bytes: val('data_received'),
            iteration_duration_ms: trend('iteration_duration'),
            http_req_waiting_ms: trend('http_req_waiting'),
            http_req_duration_ms_all: trend('http_req_duration'),
            by_endpoint: {
                order_create: endpoint('order_create'),
                cart_add: endpoint('cart_add'),
                login: endpoint('login'),               // 정상이면 reqs=0 (로그인은 setup 으로 이동)
                login_setup: endpoint('login_setup'),   // 부하 구간 밖 — 사전 발급
            },
        },
        // ---- 시나리오 지표 ----
        attempts: { total: val('order_attempts'), hot: val('order_attempts_hot'), normal: val('order_attempts_normal') },
        order_posts_total: val('order_attempts') + val('idempotency_replay_attempts') + val('timeout_retry_attempts'),
        order_http: { ok_2xx: val('order_2xx'), non_2xx: val('order_non2xx'), cart_add_fail: val('cart_add_fail') },
        idempotency: {
            replays_attempted: val('idempotency_replay_attempts'),
            replay_2xx: val('idempotency_replay_2xx'),
            replay_non2xx: val('idempotency_replay_non2xx'),
            // 타임아웃 재시도 — retry_2xx 는 "재시도가 응답을 받았다"까지만 말한다.
            retry_on_timeout: RETRY_ON_TIMEOUT,
            timeout_retry_attempts: val('timeout_retry_attempts'),
            timeout_retry_2xx: val('timeout_retry_2xx'),
            timeout_retry_non2xx: val('timeout_retry_non2xx'),
        },
        setup_token_missing: val('setup_token_missing'),          // >0 이면 사전 로그인이 일부 실패한 런
        order_create_latency_ms: trend('order_create_latency'),   // 첫 시도만 (재전송 제외)
    };

    const f = (v) => (typeof v === 'number' ? v.toFixed(1) : '?');
    const line = (t) => (t ? `med=${f(t.med)} p95=${f(t['p(95)'])} p99=${f(t['p(99)'])} max=${f(t.max)}` : 'n/a');
    const pct  = (r) => (r ? `${(r.rate * 100).toFixed(2)}%` : 'n/a');
    const ep   = (e) => `reqs=${e.reqs} failed=${pct(e.failed)} ${e.reqs > 0 ? line(e.duration_ms) : 'n/a'}`;
    const k = summary.k6;

    return {
        [`/results/${RUN_LABEL}-k6-summary.json`]: JSON.stringify(summary, null, 2),
        stdout: `\n` +
                `iterations=${k.iterations} dropped=${k.dropped_iterations} vus_max=${k.vus_max ? k.vus_max.max : '?'} http_reqs=${k.http_reqs} http_req_failed=${pct(k.http_req_failed)} checks_ok=${pct(k.checks)}\n` +
                `latency(ms) all         : ${line(k.http_req_duration_ms_all)}\n` +
                `  order_create : ${ep(k.by_endpoint.order_create)}\n` +
                `  cart_add     : ${ep(k.by_endpoint.cart_add)}\n` +
                `  login        : ${ep(k.by_endpoint.login)}   (부하 중 로그인 — 0 이어야 정상)\n` +
                `  login_setup  : ${ep(k.by_endpoint.login_setup)}   (사전 발급, 부하 구간 밖)\n` +
                `iteration_duration(ms)  : ${line(k.iteration_duration_ms)}\n` +

                `\n=== 정합성 부하 (${RUN_LABEL}) ===\n` +
                `attempts total=${summary.attempts.total} (hot=${summary.attempts.hot} normal=${summary.attempts.normal})\n` +
                `order 2xx=${summary.order_http.ok_2xx} non2xx=${summary.order_http.non_2xx} cart_fail=${summary.order_http.cart_add_fail}\n` +
                `replay overlay: replays=${summary.idempotency.replays_attempted} 2xx=${summary.idempotency.replay_2xx} non2xx=${summary.idempotency.replay_non2xx}\n` +
                `timeout retry: on=${summary.idempotency.retry_on_timeout} attempts=${summary.idempotency.timeout_retry_attempts} 2xx=${summary.idempotency.timeout_retry_2xx} non2xx=${summary.idempotency.timeout_retry_non2xx}\n` +
                `setup_token_missing=${summary.setup_token_missing}\n` +
                `order POST 총계=${summary.order_posts_total} (attempts ${summary.attempts.total} + replays ${summary.idempotency.replays_attempted} + timeout retries ${summary.idempotency.timeout_retry_attempts})\n`,
    };
}
