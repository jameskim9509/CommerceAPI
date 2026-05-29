import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useMutation } from '@tanstack/react-query'
import { changeBalance } from '@/shared/api/balance'
import { extractApiMessage } from '@/shared/api/client'

const schema = z.object({
  money: z.number().int().min(1, '1원 이상'),
  message: z.string().min(1, '메모를 입력하세요'),
  from: z.string().min(1, '출처를 입력하세요'),
})
type FormData = z.infer<typeof schema>

export function CustomerBalance() {
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormData>({
    resolver: zodResolver(schema),
    defaultValues: { money: 10000, message: '충전', from: 'self' },
  })

  const mutation = useMutation({
    mutationFn: changeBalance,
  })

  return (
    <section>
      <h1>잔액 충전</h1>
      <form onSubmit={handleSubmit((d) => mutation.mutate(d))} className="form">
        <label>
          금액 (원)
          <input type="number" {...register('money', { valueAsNumber: true })} />
          {errors.money && <small className="err">{errors.money.message}</small>}
        </label>
        <label>
          메모
          <input {...register('message')} />
          {errors.message && <small className="err">{errors.message.message}</small>}
        </label>
        <label>
          출처
          <input {...register('from')} />
          {errors.from && <small className="err">{errors.from.message}</small>}
        </label>
        <button type="submit" disabled={mutation.isPending}>
          {mutation.isPending ? '처리 중...' : '충전'}
        </button>
        {mutation.isError && <p className="err">{extractApiMessage(mutation.error)}</p>}
        {mutation.isSuccess && (
          <p className="ok">현재 잔액: {mutation.data.balance.toLocaleString()} 원</p>
        )}
      </form>
    </section>
  )
}
