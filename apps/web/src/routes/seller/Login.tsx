import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useMutation } from '@tanstack/react-query'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { loginSeller } from '@/shared/api/seller'
import { extractApiMessage } from '@/shared/api/client'
import { useAuth } from '@/shared/auth/AuthContext'

const schema = z.object({
  email: z.email('올바른 이메일을 입력하세요'),
  password: z.string().min(1, '비밀번호를 입력하세요'),
})
type FormData = z.infer<typeof schema>

export function SellerLogin() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from ?? '/seller'

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormData>({ resolver: zodResolver(schema) })

  const loginMutation = useMutation({
    mutationFn: loginSeller,
    onSuccess: (token) => {
      login(token)
      navigate(from, { replace: true })
    },
  })

  return (
    <section>
      <h1>로그인 (SELLER)</h1>
      <form onSubmit={handleSubmit((data) => loginMutation.mutate(data))} className="form">
        <label>
          이메일
          <input type="email" autoComplete="email" {...register('email')} />
          {errors.email && <small className="err">{errors.email.message}</small>}
        </label>
        <label>
          비밀번호
          <input type="password" autoComplete="current-password" {...register('password')} />
          {errors.password && <small className="err">{errors.password.message}</small>}
        </label>
        <button type="submit" disabled={loginMutation.isPending}>
          {loginMutation.isPending ? '로그인 중...' : '로그인'}
        </button>
        {loginMutation.isError && <p className="err">{extractApiMessage(loginMutation.error)}</p>}
      </form>
      <p>
        계정이 없나요? <Link to="/seller/signup">회원가입</Link>
      </p>
    </section>
  )
}
