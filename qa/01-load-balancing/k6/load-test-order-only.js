// =============================================================================
// ADR-005 시나리오 3 — order-only 버전
//
// 목적: cart_add 가 자연 throttle 역할을 해서 order endpoint 가 burst 부하를 못 받는
//      문제를 해소. setup() 단계에서 모든 VU 의 카트를 미리 채워두고, default 함수는
//      **POST /customer/cart/order 만 측정**한다.
//
// 차이점 vs load-test.js:
//   - 사용자 풀: 200 (VU 와 1:1 매핑, race 회피)
//   - setup: 각 사용자 login + 카트에 count=10_000 추가
//   - default: order 만 호출. 매 iteration 카트의 count 가 1 씩 감소.
//
// 환경변수:
//   - GATEWAY_URL  (기본: http://gateway)
//   - VU_USER_COUNT (기본: 200, 시드된 customer 수와 같거나 적어야 함)
//   - PER_USER_CART_COUNT (기본: 10000)
//   - EXPERIMENT_LABEL (예: E1o, E2o, E3o)
// =============================================================================

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const GATEWAY_URL = __ENV.GATEWAY_URL || 'http://gateway';
const VU_USER_COUNT = parseInt(__ENV.VU_USER_COUNT || '200');
const PER_USER_CART_COUNT = parseInt(__ENV.PER_USER_CART_COUNT || '10000');
const EXPERIMENT_LABEL = __ENV.EXPERIMENT_LABEL || 'unknown';

const orderLatency = new Trend('order_create_latency', true);
const instanceHits = new Counter('instance_hits');
const idempotencyReplays = new Counter('idempotency_replay_attempts');
const duplicateOrderResponses = new Counter('duplicate_order_responses');

export const options = {
    scenarios: {
        order_only: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 50 },
                { duration: '1m',  target: 200 },
                { duration: '3m',  target: 200 },   // steady — 본 측정
                { duration: '30s', target: 0 },
            ],
            gracefulRampDown: '10s',
        },
    },
    thresholds: {
        'http_req_duration{name:order_create}': ['p(95)<200', 'p(99)<500'],
        'http_req_failed{name:order_create}': ['rate<0.01'],
    },
    tags: { experiment: EXPERIMENT_LABEL },
};

// ── setup: 200 사용자 login + 카트 사전 시딩 ──
export function setup() {
    console.log(`[setup] ${VU_USER_COUNT} 사용자 로그인 + 카트 시딩 시작 ...`);
    const t0 = Date.now();
    const users = [];

    for (let i = 1; i <= VU_USER_COUNT; i++) {
        const email = `customer${i}@qa.test`;

        // 1) login
        const loginRes = http.post(
            `${GATEWAY_URL}/user/customer/login`,
            JSON.stringify({ email, password: 'password' }),
            { headers: { 'Content-Type': 'application/json' } }
        );
        if (loginRes.status !== 200) {
            console.error(`[setup] login fail ${email}: ${loginRes.status} ${loginRes.body}`);
            continue;
        }
        const token = loginRes.body.replace(/^"|"$/g, '');

        // 2) cart 시딩 — productItem id=1 (QaProduct001-Item1, price=1000) 에 count=PER_USER_CART_COUNT
        const cartBody = {
            id: 1,
            sellerId: 1,
            name: 'QaProduct001',
            description: 'Product description 1',
            productItemList: [{
                id: 1,
                name: 'QaProduct001-Item1',
                count: PER_USER_CART_COUNT,
                price: 1000,
            }],
        };
        const cartRes = http.post(`${GATEWAY_URL}/order/customer/cart`, JSON.stringify(cartBody), {
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}`,
            },
        });
        if (cartRes.status !== 200) {
            console.error(`[setup] cart fail ${email}: ${cartRes.status} ${cartRes.body}`);
            continue;
        }

        users.push({ email, token });
    }

    const dt = ((Date.now() - t0) / 1000).toFixed(1);
    console.log(`[setup] 완료 — ${users.length} 사용자 준비, ${dt} 초 소요`);
    return { users };
}

// ── default: order 만 측정 ──
export default function (data) {
    // VU 와 사용자 1:1 매핑 → 같은 사용자의 카트에 다른 VU 가 동시 접근하지 않음
    const userIndex = (__VU - 1) % data.users.length;
    const user = data.users[userIndex];
    if (!user) {
        sleep(1);
        return;
    }

    const idempotencyKey = uuidv4();
    const orderBody = {
        messages: [],
        productList: [{
            id: 1,
            sellerId: 1,
            name: 'QaProduct001',
            description: 'Product description 1',
            productItemList: [{
                id: 1,
                name: 'QaProduct001-Item1',
                count: 1,
                price: 1000,
            }],
        }],
    };

    const orderRes = http.post(`${GATEWAY_URL}/order/customer/cart/order`, JSON.stringify(orderBody), {
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${user.token}`,
            'Idempotency-Key': idempotencyKey,
        },
        tags: { name: 'order_create' },
    });

    check(orderRes, {
        'order status is 200': (r) => r.status === 200,
        'order has X-Instance-Id': (r) => !!(r.headers['X-Instance-Id'] || r.headers['x-instance-id']),
    });

    const instanceId = orderRes.headers['X-Instance-Id'] || orderRes.headers['x-instance-id'] || 'unknown';
    instanceHits.add(1, { instance: instanceId });
    orderLatency.add(orderRes.timings.duration, { instance: instanceId });

    // 5% 확률로 같은 Idempotency-Key 재전송 (멱등성 검증)
    if (Math.random() < 0.05) {
        idempotencyReplays.add(1);
        const replay = http.post(`${GATEWAY_URL}/order/customer/cart/order`, JSON.stringify(orderBody), {
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${user.token}`,
                'Idempotency-Key': idempotencyKey,
            },
            tags: { name: 'order_replay' },
        });
        if (replay.status === 200 && replay.body !== orderRes.body) {
            duplicateOrderResponses.add(1);
        }
    }

    // think time 없음 — burst 부하 유지
}

export function handleSummary(data) {
    const summary = {
        experiment: EXPERIMENT_LABEL,
        timestamp: new Date().toISOString(),
        full_metrics: data.metrics,
    };
    return {
        [`/results/${EXPERIMENT_LABEL}-summary.json`]: JSON.stringify(summary, null, 2),
        stdout: textSummary(data),
    };
}

function textSummary(data) {
    const lines = [];
    const fmt = (v) => (typeof v === 'number') ? v.toFixed(1) : 'n/a';
    const fmtPct = (v) => (typeof v === 'number') ? (v * 100).toFixed(2) + '%' : 'n/a';

    lines.push(`\n=== ADR-005 시나리오 3 (order-only) 결과 (${EXPERIMENT_LABEL}) ===\n`);

    const od = data.metrics['http_req_duration{name:order_create}'];
    if (od && od.values) {
        lines.push(`order_create p50:  ${fmt(od.values['med'])} ms`);
        lines.push(`order_create p90:  ${fmt(od.values['p(90)'])} ms`);
        lines.push(`order_create p95:  ${fmt(od.values['p(95)'])} ms`);
        lines.push(`order_create avg:  ${fmt(od.values['avg'])} ms`);
        lines.push(`order_create max:  ${fmt(od.values['max'])} ms`);
    }
    const of = data.metrics['http_req_failed{name:order_create}'];
    if (of && of.values) {
        lines.push(`order_create fail rate: ${fmtPct(of.values.rate)}`);
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
