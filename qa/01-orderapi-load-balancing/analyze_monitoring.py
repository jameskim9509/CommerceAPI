"""monitor-stats.sh에서 수집한 모니터링 데이터를 분석하여 출력한다."""
import csv
import re
from pathlib import Path
from collections import defaultdict


def parse_mysql(label: str, results: Path):
    """timestamp,threads_connected\tthreads_running\tquestions"""
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
            if len(rest) >= 3:
                try:
                    tc = int(rest[0]); tr = int(rest[1]); q = int(rest[2])
                    rows.append((ts, tc, tr, q))
                except ValueError:
                    pass
    return rows


def parse_stats(label: str, results: Path):
    """timestamp,container,cpu_pct"""
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

    for label in ('1_instance', '2_instance', '4_instance'):
        print(f'\n========== {label} ==========')

        # 인스턴스별 CPU 사용률 + 요청 균등 분배 (orderapi 인스턴스 CPU 편차)
        stats = parse_stats(label, results)
        print('  -- 인스턴스별 CPU 사용률 (%) --')
        order_cpus = []
        for container in sorted(stats.keys()):
            s = summarize_cpu(stats[container])
            if not s:
                continue
            print(f'    {container:24} avg={s["avg"]:5.1f}  p95={s["p95"]:5.1f}  max={s["max"]:5.1f}')
            if re.search(r'-orderapi-\d+$', container):
                order_cpus.append(s['avg'])
        if len(order_cpus) >= 2:
            mean = sum(order_cpus) / len(order_cpus)
            if mean > 0:
                dev = max(abs(c - mean) for c in order_cpus) / mean * 100
                print(f'  -- 요청 균등 분배 (orderapi CPU 편차) --  최대 ±{dev:.1f}%  (0%=완전 균등)')

        # MySQL 포화 (스레드)
        mysql = parse_mysql(label, results)
        if mysql:
            running = [r[2] for r in mysql]
            n = len(running)
            avg_r = sum(running) / n
            max_r = max(running)
            print(f'  -- MySQL 포화 (스레드) --  평균 {avg_r:.1f} / 최대 {max_r}')

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
                    print(f'  -- MySQL 처리량 --  queries/sec = {qps:.1f}  (Δt={dt}s)')


if __name__ == '__main__':
    main()
