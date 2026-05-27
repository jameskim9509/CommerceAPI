## E2 — orderApi 인스턴스 2 개

- 측정 시작: 2026-05-27T01:18:27Z
- 측정 종료: 2026-05-27T01:23:33Z

### 주문 상태 분포
mysql: [Warning] Using a password on the command line interface can be insecure.
status	cnt
FAILED	5717
PENDING	3660

### Outbox 미발행 잔여
mysql: [Warning] Using a password on the command line interface can be insecure.
unsent
0

### SAGA end-to-end p99 (created → updated)
mysql: [Warning] Using a password on the command line interface can be insecure.
n	avg_ms	max_ms
5819	210001.10521499	270173.6900
