## E1 — orderApi 인스턴스 1 개

- 측정 시작: 2026-05-27T04:32:56Z
- 측정 종료: 2026-05-27T04:38:05Z

### 주문 상태 분포
mysql: [Warning] Using a password on the command line interface can be insecure.
status	cnt
FAILED	6221
PENDING	3294

### Outbox 미발행 잔여
mysql: [Warning] Using a password on the command line interface can be insecure.
unsent
0

### SAGA end-to-end p99 (created → updated)
mysql: [Warning] Using a password on the command line interface can be insecure.
n	avg_ms	max_ms
6405	226713.54379204	287611.6450
