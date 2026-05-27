import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router-dom'
import { getCart, orderCart, updateCart } from '@/shared/api/cart'
import { extractApiMessage } from '@/shared/api/client'
import type { Cart } from '@/types/dto'

function newIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID()
  }
  return `${Date.now()}-${Math.random().toString(36).slice(2)}`
}

export function CustomerCart() {
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  const cartQuery = useQuery({
    queryKey: ['cart'],
    queryFn: getCart,
  })

  const updateMutation = useMutation({
    mutationFn: updateCart,
    onSuccess: (next) => queryClient.setQueryData(['cart'], next),
  })

  const orderMutation = useMutation({
    mutationFn: ({ cart, key }: { cart: Cart; key: string }) => orderCart(cart, key),
    onSuccess: (res) => {
      queryClient.invalidateQueries({ queryKey: ['cart'] })
      const orderId = (res as { orderId?: number } | null)?.orderId
      if (orderId) {
        navigate(`/customer/orders/${orderId}`)
      } else {
        alert('주문이 접수되었습니다.')
      }
    },
  })

  if (cartQuery.isLoading) return <p>로딩...</p>
  if (cartQuery.isError) return <p className="err">{extractApiMessage(cartQuery.error)}</p>

  const cart = cartQuery.data
  if (!cart || !cart.productList?.length) {
    return (
      <section>
        <h1>장바구니</h1>
        <p>
          장바구니가 비어 있습니다. <Link to="/customer/products">상품 보러 가기</Link>
        </p>
      </section>
    )
  }

  const updateItemCount = (productId: number, itemId: number, count: number) => {
    const next: Cart = {
      ...cart,
      productList: cart.productList.map((p) =>
        p.id !== productId
          ? p
          : {
              ...p,
              productItemList: p.productItemList.map((it) =>
                it.id === itemId ? { ...it, count: Math.max(0, count) } : it,
              ),
            },
      ),
    }
    updateMutation.mutate(next)
  }

  const onOrder = () => orderMutation.mutate({ cart, key: newIdempotencyKey() })

  const total = cart.productList
    .flatMap((p) => p.productItemList)
    .reduce((sum, it) => sum + it.price * it.count, 0)

  return (
    <section>
      <h1>장바구니</h1>
      {cart.messages && cart.messages.length > 0 && (
        <ul className="err">
          {cart.messages.map((m, i) => (
            <li key={i}>{m}</li>
          ))}
        </ul>
      )}
      {cart.productList.map((p) => (
        <div key={p.id} style={{ marginBottom: '1rem' }}>
          <h3>{p.name}</h3>
          <table className="table">
            <thead>
              <tr>
                <th>옵션</th>
                <th>가격</th>
                <th>수량</th>
                <th>소계</th>
              </tr>
            </thead>
            <tbody>
              {p.productItemList.map((it) => (
                <tr key={it.id}>
                  <td>{it.name}</td>
                  <td>{it.price.toLocaleString()} 원</td>
                  <td>
                    <input
                      type="number"
                      min={0}
                      value={it.count}
                      onChange={(e) => updateItemCount(p.id, it.id, Number(e.target.value))}
                      style={{ width: 80 }}
                    />
                  </td>
                  <td>{(it.price * it.count).toLocaleString()} 원</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ))}
      <p>
        <strong>합계: {total.toLocaleString()} 원</strong>
      </p>
      <button type="button" onClick={onOrder} disabled={orderMutation.isPending}>
        {orderMutation.isPending ? '주문 중...' : '주문하기'}
      </button>
      {updateMutation.isError && (
        <p className="err">{extractApiMessage(updateMutation.error)}</p>
      )}
      {orderMutation.isError && (
        <p className="err">{extractApiMessage(orderMutation.error)}</p>
      )}
    </section>
  )
}
