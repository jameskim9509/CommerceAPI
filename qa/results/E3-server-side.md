## E3 — orderApi 인스턴스 4 개

- 측정 시작: 2026-05-26T15:18:03Z
- 측정 종료: 2026-05-26T15:23:10Z

### 주문 상태 분포
mysql: [Warning] Using a password on the command line interface can be insecure.
status	cnt
FAILED	5623
PENDING	2574

### Outbox 미발행 잔여
mysql: [Warning] Using a password on the command line interface can be insecure.
unsent
0

### SAGA end-to-end p99 (created → updated)
mysql: [Warning] Using a password on the command line interface can be insecure.
n	avg_ms	max_ms
5716	199214.39956788	260716.9280
