## E3 — orderApi 인스턴스 4 개

- 측정 시작: 2026-05-27T04:50:08Z
- 측정 종료: 2026-05-27T04:55:16Z

### 주문 상태 분포
mysql: [Warning] Using a password on the command line interface can be insecure.
status	cnt
FAILED	6045
PENDING	3622

### Outbox 미발행 잔여
mysql: [Warning] Using a password on the command line interface can be insecure.
unsent
0

### SAGA end-to-end p99 (created → updated)
mysql: [Warning] Using a password on the command line interface can be insecure.
n	avg_ms	max_ms
6493	231173.43451086	291356.8810
