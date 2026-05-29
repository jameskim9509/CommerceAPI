import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useNavigate } from 'react-router-dom'

const schema = z.object({
  orderId: z.string().regex(/^\d+$/, '숫자만 입력하세요'),
})
type FormData = z.infer<typeof schema>

export function CustomerOrders() {
  const navigate = useNavigate()
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormData>({ resolver: zodResolver(schema) })

  return (
    <section>
      <h1>주문 조회</h1>
      <p>주문 ID 로 주문 상세를 조회합니다. (백엔드에 주문 목록 API 가 아직 없음 — 주문 후 받은 ID 입력)</p>
      <form
        onSubmit={handleSubmit((d) => navigate(`/customer/orders/${d.orderId}`))}
        className="form"
      >
        <label>
          주문 ID
          <input inputMode="numeric" {...register('orderId')} />
          {errors.orderId && <small className="err">{errors.orderId.message}</small>}
        </label>
        <button type="submit">조회</button>
      </form>
    </section>
  )
}
