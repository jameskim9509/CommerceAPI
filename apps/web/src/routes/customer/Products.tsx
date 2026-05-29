import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { searchProductsByName } from '@/shared/api/search'
import { extractApiMessage } from '@/shared/api/client'

const schema = z.object({
  name: z.string().min(1, '검색어를 입력하세요'),
})
type FormData = z.infer<typeof schema>

export function CustomerProducts() {
  const [query, setQuery] = useState('')
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormData>({ resolver: zodResolver(schema) })

  const { data, isLoading, isError, error, isFetching } = useQuery({
    queryKey: ['searchProducts', query],
    queryFn: () => searchProductsByName(query),
    enabled: query.length > 0,
  })

  return (
    <section>
      <h1>상품 검색</h1>
      <form onSubmit={handleSubmit((d) => setQuery(d.name))} className="form">
        <label>
          상품명
          <input {...register('name')} placeholder="예: 노트북" />
          {errors.name && <small className="err">{errors.name.message}</small>}
        </label>
        <button type="submit">검색</button>
      </form>

      {query && (isLoading || isFetching) && <p>검색 중...</p>}
      {isError && <p className="err">{extractApiMessage(error)}</p>}
      {data &&
        (data.length === 0 ? (
          <p>검색 결과가 없습니다.</p>
        ) : (
          <ul className="card-list">
            {data.map((p) => (
              <li key={p.id} className="card">
                <h3>
                  <Link to={`/customer/products/${p.id}`}>{p.name}</Link>
                </h3>
                <p className="muted">{p.description}</p>
                <p className="muted">옵션 {p.productItemList?.length ?? 0}개</p>
              </li>
            ))}
          </ul>
        ))}
    </section>
  )
}
