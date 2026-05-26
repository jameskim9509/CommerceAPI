## E2 — orderApi 인스턴스 2 개

- 측정 시작: 2026-05-26T15:10:22Z
- 측정 종료: 2026-05-26T15:15:28Z

### 주문 상태 분포
mysql: [Warning] Using a password on the command line interface can be insecure.
status	cnt
FAILED	5718
PENDING	2613

### Outbox 미발행 잔여
mysql: [Warning] Using a password on the command line interface can be insecure.
unsent
0

### SAGA end-to-end p99 (created → updated)
mysql: [Warning] Using a password on the command line interface can be insecure.
n	avg_ms	max_ms
5827	197193.25021452	267073.0740
