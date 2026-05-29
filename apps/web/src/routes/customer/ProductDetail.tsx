import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate, useParams } from 'react-router-dom'
import { getProductDetail } from '@/shared/api/search'
import { addCart } from '@/shared/api/cart'
import { extractApiMessage } from '@/shared/api/client'
import type { AddProductCartForm } from '@/types/dto'

export function CustomerProductDetail() {
  const { id } = useParams<{ id: string }>()
  const productId = Number(id)
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const [counts, setCounts] = useState<Record<number, number>>({})

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['product', productId],
    queryFn: () => getProductDetail(productId),
    enabled: Number.isFinite(productId) && productId > 0,
  })

  const addToCart = useMutation({
    mutationFn: (form: AddProductCartForm) => addCart(form),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['cart'] })
      navigate('/customer/cart')
    },
  })

  if (isLoading) return <p>로딩...</p>
  if (isError) return <p className="err">{extractApiMessage(error)}</p>
  if (!data) return <p>상품을 찾을 수 없습니다.</p>

  const onAddToCart = () => {
    const items = (data.productItemList ?? [])
      .map((it) => ({ ...it, count: counts[it.id] ?? 0 }))
      .filter((it) => it.count > 0)
    if (items.length === 0) {
      alert('수량을 1개 이상 입력하세요.')
      return
    }
    addToCart.mutate({
      id: data.id,
      // ProductDto 응답에 sellerId 가 없어 0 으로 전송. 백엔드가 product id 로 채워주는 형태 가정.
      sellerId: 0,
      name: data.name,
      description: data.description,
      productItemList: items,
    })
  }

  return (
    <section>
      <h1>{data.name}</h1>
      <p className="muted">{data.description}</p>
      <h2>옵션</h2>
      <table className="table">
        <thead>
          <tr>
            <th>옵션명</th>
            <th>가격</th>
            <th>재고</th>
            <th>담을 수량</th>
          </tr>
        </thead>
        <tbody>
          {(data.productItemList ?? []).map((it) => (
            <tr key={it.id}>
              <td>{it.name}</td>
              <td>{it.price.toLocaleString()} 원</td>
              <td>{it.count}</td>
              <td>
                <input
                  type="number"
                  min={0}
                  max={it.count}
                  value={counts[it.id] ?? 0}
                  onChange={(e) =>
                    setCounts((prev) => ({
                      ...prev,
                      [it.id]: Math.max(0, Math.min(it.count, Number(e.target.value))),
                    }))
                  }
                  style={{ width: 80 }}
                />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <button type="button" onClick={onAddToCart} disabled={addToCart.isPending}>
        {addToCart.isPending ? '담는 중...' : '장바구니 담기'}
      </button>
      {addToCart.isError && <p className="err">{extractApiMessage(addToCart.error)}</p>}
    </section>
  )
}
