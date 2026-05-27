## E1 — orderApi 인스턴스 1 개

- 측정 시작: 2026-05-27T01:10:38Z
- 측정 종료: 2026-05-27T01:15:43Z

### 주문 상태 분포
mysql: [Warning] Using a password on the command line interface can be insecure.
status	cnt
FAILED	5950
PENDING	3812

### Outbox 미발행 잔여
mysql: [Warning] Using a password on the command line interface can be insecure.
unsent
0

### SAGA end-to-end p99 (created → updated)
mysql: [Warning] Using a password on the command line interface can be insecure.
n	avg_ms	max_ms
6043	225202.17205279	287469.1650
