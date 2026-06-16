#!/usr/bin/env bash
# =============================================================================
# 정합성 통합 시나리오 — 부하 종료 후 3대 불변식 검증 (교차 DB)
#
#   ① 멱등성 위반        = 0  (k6 duplicate_order_responses)
#   ② 초과판매            = 0  (재고 음수 0, 차감량 == CONFIRMED 수량, CONFIRMED <= 초기재고)
#   ③ 결제·재고 정합성    = 0  (PENDING/PAID 잔여 0, outbox 미발행 0(양 DB),
#                               돈 보존: 잔액 감소액 == CONFIRMED 결제총액, 음수 잔액 0)
#
# 사용:  bash qa/verify-consistency.sh
# =============================================================================
export MSYS_NO_PATHCONV=1
set -uo pipefail
cd "$(dirname "$0")/.."

CF=(-f qa/docker-compose.qa.yml -f qa/docker-compose.consistency.yml)

# seed 와 일치해야 하는 상수
HOT_ID=10001
HOT_INIT=1200
SEED_RICH=300;  RICH_BAL=10000000
SEED_BROKE=60;  BROKE_BAL=500
BAL_INIT=$(( SEED_RICH*RICH_BAL + SEED_BROKE*BROKE_BAL ))
SUMMARY="qa/results/CONSIST-summary.json"
SCOPE="(username LIKE 'ctrich%' OR username LIKE 'ctbroke%')"

qo(){ docker compose "${CF[@]}" exec -T mysql-order mysql -uroot -proot -N -B orders -e "$1" 2>/dev/null; }
qu(){ docker compose "${CF[@]}" exec -T mysql-user  mysql -uroot -proot -N -B user   -e "$1" 2>/dev/null; }

echo "=== 주문 상태 분포 ==="
qo "SELECT status, COUNT(*) FROM orders WHERE $SCOPE GROUP BY status;"
echo ""
echo "=== FAILED 사유 분포 (보상 경로 발화 증거) ==="
qo "SELECT failure_reason, COUNT(*) FROM orders WHERE status='FAILED' AND $SCOPE GROUP BY failure_reason;"
echo ""

confirmed_n=$(qo "SELECT COUNT(*) FROM orders WHERE status='CONFIRMED' AND $SCOPE;")
failed_n=$(qo    "SELECT COUNT(*) FROM orders WHERE status='FAILED'    AND $SCOPE;")
pending_paid=$(qo "SELECT COUNT(*) FROM orders WHERE status IN ('PENDING','PAID') AND $SCOPE;")
hot_count=$(qo   "SELECT count FROM product_item WHERE id=$HOT_ID;")
hot_version=$(qo "SELECT version FROM product_item WHERE id=$HOT_ID;")
confirmed_qty=$(qo "SELECT COALESCE(SUM(oi.count),0) FROM order_items oi JOIN orders o ON oi.order_id=o.id WHERE o.status='CONFIRMED' AND oi.product_item_id=$HOT_ID;")
deducted_total=$(qo "SELECT COALESCE(SUM(total_price),0) FROM orders WHERE status='CONFIRMED' AND $SCOPE;")
neg_stock=$(qo   "SELECT COUNT(*) FROM product_item WHERE count<0;")
outbox_order_unsent=$(qo "SELECT COUNT(*) FROM outbox_events WHERE sent_at IS NULL;")

outbox_user_unsent=$(qu "SELECT COUNT(*) FROM outbox_events WHERE sent_at IS NULL;")
balance_now=$(qu "SELECT COALESCE(SUM(balance),0) FROM customer WHERE email LIKE 'ctrich%' OR email LIKE 'ctbroke%';")
neg_balance=$(qu "SELECT COUNT(*) FROM customer WHERE balance<0 AND (email LIKE 'ctrich%' OR email LIKE 'ctbroke%');")

dup=$(sed -n 's/.*"duplicate_order_responses": *\([0-9][0-9]*\).*/\1/p' "$SUMMARY" 2>/dev/null); dup=${dup:-NA}
replays=$(sed -n 's/.*"idempotency_replay_attempts": *\([0-9][0-9]*\).*/\1/p' "$SUMMARY" 2>/dev/null); replays=${replays:-NA}

balance_drop=$(( BAL_INIT - balance_now ))
stock_consumed=$(( HOT_INIT - hot_count ))

echo "=== 측정값 ==="
printf "CONFIRMED=%s  FAILED=%s  PENDING/PAID=%s\n" "$confirmed_n" "$failed_n" "$pending_paid"
printf "한정SKU: 초기재고=%s  최종재고=%s  version=%s  소비량=%s  CONFIRMED수량=%s\n" "$HOT_INIT" "$hot_count" "$hot_version" "$stock_consumed" "$confirmed_qty"
printf "결제차감총액(CONFIRMED)=%s  잔액초기합=%s  잔액현재합=%s  잔액감소=%s\n" "$deducted_total" "$BAL_INIT" "$balance_now" "$balance_drop"
printf "outbox 미발행: orders=%s  user=%s   음수재고=%s  음수잔액=%s\n" "$outbox_order_unsent" "$outbox_user_unsent" "$neg_stock" "$neg_balance"
printf "멱등 재전송 시도=%s  멱등성 위반(중복주문)=%s\n" "$replays" "$dup"
echo ""

pass=0; fail=0
chk(){ if [ "$2" = "PASS" ]; then echo "  ✅ $1"; pass=$((pass+1)); else echo "  ❌ $1  ($3)"; fail=$((fail+1)); fi; }
v(){ [ "$1" = "$2" ] && echo PASS || echo FAIL; }
le(){ [ "$1" -le "$2" ] 2>/dev/null && echo PASS || echo FAIL; }

echo "=== 불변식 판정 ==="
# ① 멱등성
chk "① 멱등성 위반 = 0" "$(v "$dup" 0)" "duplicate=$dup"
# ② 초과판매
chk "② 재고 음수 없음" "$(v "$neg_stock" 0)" "neg_stock=$neg_stock"
chk "② 차감량 == CONFIRMED 수량" "$(v "$stock_consumed" "$confirmed_qty")" "$stock_consumed vs $confirmed_qty"
chk "② CONFIRMED수량 <= 초기재고" "$(le "$confirmed_qty" "$HOT_INIT")" "$confirmed_qty > $HOT_INIT"
# ③ SAGA / 정합성
chk "③ PENDING/PAID 잔여 = 0" "$(v "$pending_paid" 0)" "pending_paid=$pending_paid"
chk "③ outbox 미발행 = 0 (orders)" "$(v "$outbox_order_unsent" 0)" "$outbox_order_unsent"
chk "③ outbox 미발행 = 0 (user)" "$(v "$outbox_user_unsent" 0)" "$outbox_user_unsent"
chk "③ 돈 보존: 잔액감소 == 결제총액" "$(v "$balance_drop" "$deducted_total")" "$balance_drop vs $deducted_total"
chk "③ 음수 잔액 없음" "$(v "$neg_balance" 0)" "neg_balance=$neg_balance"

echo ""
echo "================ 결과: PASS=$pass  FAIL=$fail ================"
[ "$fail" -eq 0 ] && echo "🎉 세 불변식(멱등성·초과판매·결제재고정합성) 모두 0 위반" || echo "⚠️ 위반 존재 — 위 ❌ 항목 확인"
