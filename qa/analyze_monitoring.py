"""Analyze monitoring CSVs: per-container CPU profile during k6 steady state."""
import csv
import re
from pathlib import Path
from collections import defaultdict


def parse_mysql(label: str, results: Path):
    """timestamp,threads_connected\tthreads_running\tquestions\tslow_queries"""
    rows = []
    with (results / f'{label}-mysql.csv').open(encoding='utf-8') as f:
        next(f)  # header
        for line in f:
            line = line.strip()
            if not line:
                continue
            parts = line.split(',', 1)
            if len(parts) < 2:
                continue
            ts = parts[0]
            rest = parts[1].split('\t')
            if len(rest) >= 4:
                try:
                    tc = int(rest[0]); tr = int(rest[1]); q = int(rest[2]); sq = int(rest[3])
                    rows.append((ts, tc, tr, q, sq))
                except ValueError:
                    pass
    return rows


def parse_stats(label: str, results: Path):
    """timestamp,container,cpu_pct,mem_usage,mem_pct,net_io,block_io"""
    by_container = defaultdict(list)
    with (results / f'{label}-stats.csv').open(encoding='utf-8') as f:
        next(f)  # header
        for line in f:
            parts = line.strip().split(',')
            if len(parts) < 3:
                continue
            ts, container, cpu = parts[0], parts[1], parts[2]
            try:
                cpu_val = float(cpu)
            except ValueError:
                continue
            by_container[container].append((ts, cpu_val))
    return by_container


def summarize_cpu(samples):
    """Return min/avg/max/p95 from list of (ts, cpu)."""
    vals = sorted(v for _, v in samples)
    if not vals:
        return None
    n = len(vals)
    avg = sum(vals) / n
    return {
        'n': n,
        'min': vals[0],
        'avg': avg,
        'max': vals[-1],
        'p50': vals[n // 2],
        'p95': vals[int(n * 0.95)],
    }


def main():
    results = Path(__file__).parent / 'results'

    for label in ('E1', 'E2', 'E3'):
        print(f'\n========== {label} ==========')

        # 컨테이너별 CPU 통계
        stats = parse_stats(label, results)
        for container in sorted(stats.keys()):
            s = summarize_cpu(stats[container])
            if not s:
                continue
            print(f'  {container:24} samples={s["n"]:3}  '
                  f'avg={s["avg"]:5.1f}%  p95={s["p95"]:5.1f}%  max={s["max"]:5.1f}%')

        # MySQL 상태
        mysql = parse_mysql(label, results)
        if mysql:
            running = [r[2] for r in mysql]
            connected = [r[1] for r in mysql]
            n = len(running)
            avg_r = sum(running) / n
            avg_c = sum(connected) / n
            max_r = max(running)
            max_c = max(connected)
            print(f'  MySQL Threads_running     samples={n}  avg={avg_r:.1f}  max={max_r}')
            print(f'  MySQL Threads_connected   samples={n}  avg={avg_c:.1f}  max={max_c}')

            # questions delta (queries per second)
            if len(mysql) >= 2:
                first_q = mysql[0][3]
                last_q = mysql[-1][3]
                # ts is HH:MM:SS — compute seconds delta
                def ts_to_s(ts):
                    h, m, s = ts.split(':')
                    return int(h) * 3600 + int(m) * 60 + int(s)
                dt = ts_to_s(mysql[-1][0]) - ts_to_s(mysql[0][0])
                if dt > 0:
                    qps = (last_q - first_q) / dt
                    print(f'  MySQL queries/sec (delta) over {dt}s window: {qps:.1f}')


if __name__ == '__main__':
    main()
