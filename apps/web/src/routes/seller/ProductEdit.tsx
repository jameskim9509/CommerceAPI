import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate, useParams } from 'react-router-dom'
import { getProductDetail } from '@/shared/api/search'
import {
  addProductItem,
  deleteProductItem,
  updateProduct,
  updateProductItem,
} from '@/shared/api/seller'
import { extractApiMessage } from '@/shared/api/client'

interface ItemDraft {
  id: number | null // null = 신규
  name: string
  price: number
  count: number
}

export function SellerProductEdit() {
  const { id } = useParams<{ id: string }>()
  const productId = Number(id)
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const detailQuery = useQuery({
    queryKey: ['product', productId],
    queryFn: () => getProductDetail(productId),
    enabled: Number.isFinite(productId) && productId > 0,
  })

  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [items, setItems] = useState<ItemDraft[]>([])

  // 처음 로드시 폼 초기화
  useEffect(() => {
    if (!detailQuery.data) return
    setName(detailQuery.data.name)
    setDescription(detailQuery.data.description)
    setItems(
      (detailQuery.data.productItemList ?? []).map((it) => ({
        id: it.id,
        name: it.name,
        price: it.price,
        count: it.count,
      })),
    )
  }, [detailQuery.data])

  const reloadAfterMutation = () => {
    queryClient.invalidateQueries({ queryKey: ['product', productId] })
    queryClient.invalidateQueries({ queryKey: ['seller', 'products'] })
  }

  const updateProductMutation = useMutation({
    mutationFn: updateProduct,
    onSuccess: () => reloadAfterMutation(),
  })

  const updateItemMutation = useMutation({
    mutationFn: updateProductItem,
    onSuccess: () => reloadAfterMutation(),
  })

  const addItemMutation = useMutation({
    mutationFn: addProductItem,
    onSuccess: () => reloadAfterMutation(),
  })

  const deleteItemMutation = useMutation({
    mutationFn: deleteProductItem,
    onSuccess: () => reloadAfterMutation(),
  })

  if (detailQuery.isLoading) return <p>로딩...</p>
  if (detailQuery.isError) return <p className="err">{extractApiMessage(detailQuery.error)}</p>
  if (!detailQuery.data) return <p>상품을 찾을 수 없습니다.</p>

  const onSaveProductMeta = () => {
    updateProductMutation.mutate({
      productId,
      name,
      description,
      updateProductItemForms: [], // 본문 메타만 수정. 옵션은 행별 PUT.
    })
  }

  const onSaveItem = (it: ItemDraft) => {
    if (it.id == null) {
      addItemMutation.mutate({
        productId,
        name: it.name,
        price: it.price,
        count: it.count,
      })
    } else {
      updateItemMutation.mutate({
        id: it.id,
        productId,
        name: it.name,
        price: it.price,
        count: it.count,
      })
    }
  }

  const onDeleteItem = (it: ItemDraft, idx: number) => {
    if (it.id == null) {
      setItems((prev) => prev.filter((_, i) => i !== idx))
      return
    }
    if (!confirm(`옵션 #${it.id} 삭제할까요?`)) return
    deleteItemMutation.mutate(it.id)
  }

  const addRow = () =>
    setItems((prev) => [...prev, { id: null, name: '', price: 0, count: 0 }])

  return (
    <section>
      <h1>상품 수정 #{productId}</h1>
      <div className="form" style={{ maxWidth: 640 }}>
        <label>
          상품명
          <input value={name} onChange={(e) => setName(e.target.value)} />
        </label>
        <label>
          설명
          <textarea
            rows={3}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
          />
        </label>
        <button
          type="button"
          onClick={onSaveProductMeta}
          disabled={updateProductMutation.isPending}
        >
          {updateProductMutation.isPending ? '저장 중...' : '상품 정보 저장'}
        </button>
        {updateProductMutation.isError && (
          <p className="err">{extractApiMessage(updateProductMutation.error)}</p>
        )}
      </div>

      <h2 style={{ marginTop: '2rem' }}>옵션</h2>
      <table className="table">
        <thead>
          <tr>
            <th>id</th>
            <th>옵션명</th>
            <th>가격</th>
            <th>재고</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {items.map((it, idx) => (
            <tr key={it.id ?? `new-${idx}`}>
              <td>{it.id ?? <em>new</em>}</td>
              <td>
                <input
                  value={it.name}
                  onChange={(e) =>
                    setItems((prev) =>
                      prev.map((row, i) => (i === idx ? { ...row, name: e.target.value } : row)),
                    )
                  }
                />
              </td>
              <td>
                <input
                  type="number"
                  value={it.price}
                  onChange={(e) =>
                    setItems((prev) =>
                      prev.map((row, i) =>
                        i === idx ? { ...row, price: Number(e.target.value) } : row,
                      ),
                    )
                  }
                  style={{ width: 100 }}
                />
              </td>
              <td>
                <input
                  type="number"
                  value={it.count}
                  onChange={(e) =>
                    setItems((prev) =>
                      prev.map((row, i) =>
                        i === idx ? { ...row, count: Number(e.target.value) } : row,
                      ),
                    )
                  }
                  style={{ width: 80 }}
                />
              </td>
              <td>
                <button type="button" onClick={() => onSaveItem(it)}>
                  {it.id == null ? '추가' : '저장'}
                </button>{' '}
                <button type="button" onClick={() => onDeleteItem(it, idx)}>
                  삭제
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <p>
        <button type="button" onClick={addRow}>
          ＋ 옵션 행 추가
        </button>
      </p>
      {(addItemMutation.isError ||
        updateItemMutation.isError ||
        deleteItemMutation.isError) && (
        <p className="err">
          {extractApiMessage(
            addItemMutation.error ?? updateItemMutation.error ?? deleteItemMutation.error,
          )}
        </p>
      )}

      <p style={{ marginTop: '1rem' }}>
        <button type="button" onClick={() => navigate('/seller/products')}>
          ← 목록으로
        </button>
      </p>
    </section>
  )
}
