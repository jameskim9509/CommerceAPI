"""Detailed per-endpoint latency analysis to identify bottleneck."""
import json
from pathlib import Path


def fmt(v, suffix='ms'):
    if isinstance(v, (int, float)):
        return f'{v:>8.1f} {suffix}'
    return f'{"n/a":>11}'


def main():
    results = Path(__file__).parent / 'results'
    rows = []
    for label in ('E1', 'E2', 'E3'):
        with (results / f'{label}-k6-summary.json').open(encoding='utf-8') as f:
            d = json.load(f)
        m = d['metrics']

        def metric(key):
            v = m.get(key, {})
            return {
                'count': v.get('count'),
                'avg': v.get('avg'),
                'med': v.get('med'),
                'p90': v.get('p(90)'),
                'p95': v.get('p(95)'),
                'max': v.get('max'),
            }

        rows.append((label, {
            'aggregate': metric('http_req_duration'),
            'login': metric('http_req_duration{name:login}'),
            'cart_add': metric('http_req_duration{name:cart_add}'),
            'order_create': metric('http_req_duration{name:order_create}'),
        }))

    for label, group in rows:
        print(f'\n========== {label} ==========')
        for endpoint in ('aggregate', 'login', 'cart_add', 'order_create'):
            v = group[endpoint]
            print(f'  {endpoint:14} '
                  f'count={v["count"]!s:>6}  '
                  f'med={fmt(v["med"])}  '
                  f'p90={fmt(v["p90"])}  '
                  f'p95={fmt(v["p95"])}  '
                  f'max={fmt(v["max"])}')


if __name__ == '__main__':
    main()
