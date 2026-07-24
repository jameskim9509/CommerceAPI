"""k6 결과 3개(1/2/4 instance)를 읽어 인스턴스 스케일에 따른 개선을 비교한다 (cross-run).
per-run 상세 지표(latency/throughput/에러율/분배)는 k6 handleSummary
(실행 직후 stdout · {label}-summary.json)가 담당한다. 여기서는 실험 간 비교만."""
import json
from pathlib import Path

LABELS = ('1_instance', '2_instance', '4_instance')


def load(label, results_dir):
    f = results_dir / f'{label}-k6-summary.json'
    if not f.exists():
        return None
    with f.open(encoding='utf-8') as fp:
        m = json.load(fp).get('metrics', {})
    return {
        'p99': m.get('http_req_duration{name:order_create}', {}).get('p(99)'),
        'tps': m.get('iterations', {}).get('rate'),   # 초당 완료주문수 = handleSummary throughput 과 동일 정의
    }


def pct(v):
    return f'{v:+.1f}%' if v is not None else 'n/a'


def main():
    results_dir = Path(__file__).parent / 'results'
    data = {label: load(label, results_dir) for label in LABELS}

    base = data.get('1_instance')
    if not base or base.get('p99') is None or base.get('tps') is None:
        print('1_instance 결과가 없어 비교 불가 — 먼저 1_instance 측정 필요')
        return

    print('== 인스턴스 스케일 비교 (1_instance 기준) ==')
    for label in LABELS:
        r = data.get(label)
        if not r or r.get('p99') is None or r.get('tps') is None:
            print(f'  {label}: (결과 없음)')
            continue
        if label == '1_instance':
            print(f'  {label}: p99={r["p99"]:.1f}ms, throughput={r["tps"]:.1f} 주문/s  (기준)')
        else:
            p99_drop = (base['p99'] - r['p99']) / base['p99'] * 100   # (p99_1 − p99_N)/p99_1
            tps_gain = (r['tps'] - base['tps']) / base['tps'] * 100   # (tps_N − tps_1)/tps_1
            print(f'  {label}: p99={r["p99"]:.1f}ms, throughput={r["tps"]:.1f} 주문/s'
                  f'  →  p99 감소율 {pct(p99_drop)}, throughput 증가율 {pct(tps_gain)}')


if __name__ == '__main__':
    main()
