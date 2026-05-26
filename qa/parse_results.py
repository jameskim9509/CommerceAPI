"""Parse k6 --summary-export JSON and pretty-print key metrics."""
import json
import sys
from pathlib import Path


def fmt(v, suffix=''):
    if v is None:
        return 'n/a'
    if isinstance(v, (int, float)):
        return f'{v:.1f}{suffix}'
    return str(v)


def main():
    results_dir = Path(__file__).parent / 'results'
    for label in ('E1', 'E2', 'E3'):
        f = results_dir / f'{label}-k6-summary.json'
        if not f.exists():
            print(f'== {label} (file not found) ==\n')
            continue

        with f.open(encoding='utf-8') as fp:
            d = json.load(fp)
        m = d.get('metrics', {})
        root_checks = d.get('root_group', {}).get('checks', {})

        # Sub-metrics like http_req_duration{name:order_create} appear as top-level keys
        order_dur = m.get('http_req_duration{name:order_create}', {})
        order_fail = m.get('http_req_failed{name:order_create}', {})

        # Fallback: aggregate http_req_duration if tagged variant missing
        if not order_dur:
            order_dur = m.get('http_req_duration', {})

        print(f'== {label} ==')
        print(f'  total reqs:           {m.get("http_reqs", {}).get("count")}')
        print(f'  throughput (req/s):   {fmt(m.get("http_reqs", {}).get("rate"))}')
        print(f'  iterations:           {m.get("iterations", {}).get("count")}')
        print(f'  -- order_create latency (ms) --')
        print(f'  avg:                  {fmt(order_dur.get("avg"))}')
        print(f'  median (p50):         {fmt(order_dur.get("med"))}')
        print(f'  p90:                  {fmt(order_dur.get("p(90)"))}')
        print(f'  p95:                  {fmt(order_dur.get("p(95)"))}')
        print(f'  max:                  {fmt(order_dur.get("max"))}')
        rate = order_fail.get('rate')
        print(f'  fail rate (order):    {fmt(rate * 100 if rate is not None else None, "%")}')
        print(f'  idempotency replays:  {m.get("idempotency_replay_attempts", {}).get("count")}')
        print(f'  멱등성 위반:           {m.get("duplicate_order_responses", {}).get("count")}')

        # Per-instance distribution
        inst = m.get('instance_hits', {})
        sub = inst.get('submetrics') or {}
        if sub:
            print(f'  -- 인스턴스별 분배 --')
            for name, sm in sub.items():
                print(f'    {name}: {sm.get("count")}')

        # checks
        if root_checks:
            print(f'  -- checks --')
            for name, c in root_checks.items():
                p = c.get('passes', 0)
                f_ = c.get('fails', 0)
                tot = p + f_
                pct = (p / tot * 100) if tot else 0
                print(f'    {name}: {p}/{tot} ({pct:.2f}%)')

        print()


if __name__ == '__main__':
    main()
