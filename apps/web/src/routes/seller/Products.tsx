import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { getProductListByIds } from '@/shared/api/search'
import { deleteProduct } from '@/shared/api/seller'
import { extractApiMessage } from '@/shared/api/client'
import { useAuth } from '@/shared/auth/AuthContext'
import { forgetMyProductId, getMyProductIds } from './productStore'

export function SellerProducts() {
  const { user } = useAuth()
  const queryClient = useQueryClient()

  const ids = getMyProductIds(user?.id)

  const listQuery = useQuery({
    queryKey: ['seller', 'products', user?.id, ids.join(',')],
    queryFn: () => getProductListByIds(ids),
    enabled: ids.length > 0,
  })

  const deleteMutation = useMutation({
    mutationFn: (productId: number) => deleteProduct(productId),
    onSuccess: (_msg, productId) => {
      forgetMyProductId(user?.id, productId)
      queryClient.invalidateQueries({ queryKey: ['seller', 'products'] })
    },
  })

  const onDelete = (productId: number) => {
    if (!confirm(`상품 #${productId} 을(를) 삭제할까요?`)) return
    deleteMutation.mutate(productId)
  }

  return (
    <section>
      <h1>판매자 상품 관리</h1>
      <p>
        <Link to="/seller/products/new">
          <strong>＋ 신규 상품 등록</strong>
        </Link>
      </p>

      {ids.length === 0 ? (
        <p className="muted">
          아직 등록한 상품이 없습니다. 신규 상품 등록 을 눌러 시작하세요.
          <br />
          <small>
            (※ 백엔드에 SELLER 전용 상품 목록 API 가 없어, 이 화면은 이 브라우저에서 등록한
            상품만 보여줍니다. 자세한 내용은 docs/seller-flow.md 참고.)
          </small>
        </p>
      ) : listQuery.isLoading || listQuery.isFetching ? (
        <p>불러오는 중...</p>
      ) : listQuery.isError ? (
        <p className="err">{extractApiMessage(listQuery.error)}</p>
      ) : (
        <ul className="card-list">
          {(listQuery.data ?? []).map((p) => (
            <li key={p.id} className="card">
              <h3>{p.name}</h3>
              <p className="muted">{p.description}</p>
              <p className="muted">옵션 {p.productItemList?.length ?? 0}개</p>
              <p>
                <Link to={`/seller/products/${p.id}/edit`}>수정</Link>
                {' | '}
                <button
                  type="button"
                  onClick={() => onDelete(p.id)}
                  disabled={deleteMutation.isPending}
                  style={{
                    border: 'none',
                    background: 'none',
                    color: '#c62828',
                    cursor: 'pointer',
                    padding: 0,
                  }}
                >
                  삭제
                </button>
              </p>
            </li>
          ))}
        </ul>
      )}
      {deleteMutation.isError && (
        <p className="err">{extractApiMessage(deleteMutation.error)}</p>
      )}
    </section>
  )
}
