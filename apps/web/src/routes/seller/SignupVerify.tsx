import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useMutation } from '@tanstack/react-query'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { verifySellerEmail } from '@/shared/api/seller'
import { extractApiMessage } from '@/shared/api/client'

const schema = z.object({
  email: z.email('올바른 이메일을 입력하세요'),
  code: z.string().min(1, '인증코드를 입력하세요'),
})
type FormData = z.infer<typeof schema>

export function SellerSignupVerify() {
  const [params] = useSearchParams()
  const defaultEmail = params.get('email') ?? ''
  const navigate = useNavigate()

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormData>({
    resolver: zodResolver(schema),
    defaultValues: { email: defaultEmail, code: '' },
  })

  const verifyMutation = useMutation({
    mutationFn: (data: FormData) => verifySellerEmail(data.email, data.code),
    onSuccess: () => navigate('/seller/login'),
  })

  return (
    <section>
      <h1>이메일 인증 (SELLER)</h1>
      <p>가입 시 등록한 이메일로 받은 인증코드를 입력하세요.</p>
      <form onSubmit={handleSubmit((data) => verifyMutation.mutate(data))} className="form">
        <label>
          이메일
          <input type="email" {...register('email')} />
          {errors.email && <small className="err">{errors.email.message}</small>}
        </label>
        <label>
          인증코드
          <input {...register('code')} />
          {errors.code && <small className="err">{errors.code.message}</small>}
        </label>
        <button type="submit" disabled={verifyMutation.isPending}>
          {verifyMutation.isPending ? '인증 중...' : '인증 완료'}
        </button>
        {verifyMutation.isError && <p className="err">{extractApiMessage(verifyMutation.error)}</p>}
      </form>
    </section>
  )
}
