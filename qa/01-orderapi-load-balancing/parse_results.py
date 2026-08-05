"""k6 결과 3개(1/2/4 instance)를 읽어 인스턴스 스케일에 따른 개선을 비교한다 (cross-run).
per-run 상세 지표(latency/에러율/분배)는 k6 handleSummary
(실행 직후 stdout · {label}-summary.json)가 담당한다. 여기서는 실험 간 비교만.

주 지표는 소진 시간과 p95 다.
  - 소진 시간: shared-iterations 로 주문 N 건을 다 처리하는 데 걸린 시간 = 버스트 흡수 능력.
  - p95: 버스트 중 사용자가 겪는 지연.
k6 의 iterations.rate 는 공용 풀이 비면서 동시성이 떨어지는 꼬리 구간까지 분모에 넣어
처리량을 과소평가하므로(같은 조건에서 -18% 관측) 참고값으로만 출력한다.
"""
import json
from pathlib import Path

LABELS = ('1_instance', '2_instance', '4_instance')
N_OF = {'1_instance': 1, '2_instance': 2, '4_instance': 4}


def load(label, results_dir):
    """소진 시간은 handleSummary 가 쓴 {label}-summary.json, 지연은 k6 원본 요약에서 읽는다."""
    k6f = results_dir / f'{label}-k6-summary.json'
    sumf = results_dir / f'{label}-summary.json'
    if not k6f.exists():
        return None
    with k6f.open(encoding='utf-8') as fp:
        m = json.load(fp).get('metrics', {})
    order = m.get('http_req_duration{name:order_create}', {})

    run_ms = None
    if sumf.exists():
        with sumf.open(encoding='utf-8') as fp:
            run_ms = json.load(fp).get('run_duration_ms')

    # 중단된 런(setup 도중 abort 등)도 handleSummary 는 정상 파일을 쓴다. 이때 no-op
    # threshold 로 만들어진 order_create 서브메트릭은 값이 None 이 아니라 "0" 이라,
    # p95 만 보면 무효 런이 "p95 0 ms" 라는 최고 성적으로 통과한다. 반복이 한 번도
    # 완료되지 않았다는 사실(iterations 부재/0)로 판정해야 한다.
    iters = m.get('iterations', {}).get('count')
    if not iters:
        return {'invalid': '반복 0건 — 런이 완료되지 않음 (setup 중단 등)'}

    return {
        'invalid': None,
        'run_s': run_ms / 1000 if run_ms else None,
        'p95': order.get('p(95)'),
        'p99': order.get('p(99)'),
        'tps': m.get('iterations', {}).get('rate'),
    }


def pct(v):
    return f'{v:+.1f}%' if v is not None else 'n/a'


def main():
    results_dir = Path(__file__).parent / 'results'
    data = {label: load(label, results_dir) for label in LABELS}

    base = data.get('1_instance')
    if not base or base.get('invalid') or base.get('p95') is None:
        print('1_instance 결과가 없어 비교 불가 — 먼저 1_instance 측정 필요')
        return

    print('== 인스턴스 스케일 비교 (1_instance 기준) ==')
    print(f'{"구성":<12}{"소진(s)":>9}{"p95(ms)":>10}{"p99(ms)":>10}{"주문/s":>9}'
          f'{"단축배율":>10}{"p95감소":>10}{"소진효율":>10}')
    for label in LABELS:
        r = data.get(label)
        if not r:
            print(f'  {label}: (결과 없음)')
            continue
        if r.get('invalid'):
            print(f'  {label}: ⚠ 무효 — {r["invalid"]}')
            continue
        if r.get('p95') is None:
            print(f'  {label}: (결과 없음)')
            continue
        run_s = r['run_s']
        row = (f'{label:<12}{run_s if run_s else 0:>9.1f}{r["p95"]:>10.1f}'
               f'{r["p99"]:>10.1f}{r["tps"]:>9.1f}')
        if label == '1_instance':
            print(row + f'{"(기준)":>10}')
            continue
        # 단축 배율 = 1대 소진시간 / N대 소진시간, 소진 효율 = 단축 배율 / N (선형 확장 대비)
        speedup = base['run_s'] / run_s if base['run_s'] and run_s else None
        eff = speedup / N_OF[label] * 100 if speedup else None
        p95_drop = (base['p95'] - r['p95']) / base['p95'] * 100
        print(row + f'{speedup if speedup else 0:>9.2f}x{pct(p95_drop):>10}'
                    f'{eff if eff else 0:>9.1f}%')

    print()
    print('  소진 시간은 setup(로그인) 포함값. 구성 간 USER_COUNT 가 같아야 비교 가능하다.')
    print('  주문/s 는 shared-iterations 의 꼬리 구간 때문에 과소평가된다 (참고값).')


if __name__ == '__main__':
    main()
