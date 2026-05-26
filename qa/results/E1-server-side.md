## E1 — orderApi 인스턴스 1 개

- 측정 시작: 2026-05-26T15:02:40Z
- 측정 종료: 2026-05-26T15:07:45Z

### 주문 상태 분포
mysql: [Warning] Using a password on the command line interface can be insecure.
status	cnt
FAILED	3789
PENDING	5208

### Outbox 미발행 잔여
mysql: [Warning] Using a password on the command line interface can be insecure.
unsent
0

### SAGA end-to-end p99 (created → updated)
mysql: [Warning] Using a password on the command line interface can be insecure.
n	avg_ms	max_ms
3872	247348.39916761	287921.8570
