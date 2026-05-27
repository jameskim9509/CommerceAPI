## E3 — orderApi 인스턴스 4 개

- 측정 시작: 2026-05-27T01:26:09Z
- 측정 종료: 2026-05-27T01:31:16Z

### 주문 상태 분포
mysql: [Warning] Using a password on the command line interface can be insecure.
status	cnt
FAILED	5864
PENDING	2986

### Outbox 미발행 잔여
mysql: [Warning] Using a password on the command line interface can be insecure.
unsent
0

### SAGA end-to-end p99 (created → updated)
mysql: [Warning] Using a password on the command line interface can be insecure.
n	avg_ms	max_ms
5974	207134.19014563	260737.4360
