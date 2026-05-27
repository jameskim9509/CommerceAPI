import { useQuery } from '@tanstack/react-query'
import { useParams } from 'react-router-dom'
import { getOrder } from '@/shared/api/order'
import { extractApiMessage } from '@/shared/api/client'
import type { OrderStatus } from '@/types/dto'

const statusLabel: Record<OrderStatus, string> = {
  PENDING: '주문 처리 중',
  PAID: '결제 완료 (재고 확정 대기)',
  CONFIRMED: '주문 확정',
  FAILED: '주문 실패',
}

export function CustomerOrderDetail() {
  const { orderId } = useParams<{ orderId: string }>()
  const id = Number(orderId)

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['order', id],
    queryFn: () => getOrder(id),
    enabled: Number.isFinite(id) && id > 0,
    // ADR-003 SAGA 진행 중인 PENDING/PAID 상태에선 2 초 폴링
    refetchInterval: (q) => {
      const d = q.state.data
      return d && (d.status === 'PENDING' || d.status === 'PAID') ? 2000 : false
    },
  })

  if (isLoading) return <p>로딩...</p>
  if (isError) return <p className="err">{extractApiMessage(error)}</p>
  if (!data) return <p>주문 없음</p>

  return (
    <section>
      <h1>주문 #{data.orderId}</h1>
      <p>
        상태: <strong>{statusLabel[data.status] ?? data.status}</strong>
      </p>
      {data.failureReason && <p className="err">실패 사유: {data.failureReason}</p>}

      <h2>주문 항목</h2>
      <table className="table">
        <thead>
          <tr>
            <th>상품</th>
            <th>수량</th>
            <th>가격</th>
            <th>소계</th>
          </tr>
        </thead>
        <tbody>
          {data.items.map((it) => (
            <tr key={it.productItemId}>
              <td>{it.name}</td>
              <td>{it.count}</td>
              <td>{it.price.toLocaleString()} 원</td>
              <td>{(it.count * it.price).toLocaleString()} 원</td>
            </tr>
          ))}
        </tbody>
      </table>
      <p>
        <strong>총액: {data.totalPrice.toLocaleString()} 원</strong>
      </p>
    </section>
  )
}
