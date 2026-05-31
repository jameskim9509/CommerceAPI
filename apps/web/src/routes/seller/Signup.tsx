import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useMutation } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router-dom'
import { signupSeller } from '@/shared/api/seller'
import { extractApiMessage } from '@/shared/api/client'

const schema = z.object({
  email: z.email('올바른 이메일을 입력하세요'),
  name: z.string().min(1, '이름을 입력하세요'),
  password: z.string().min(8, '비밀번호는 8자 이상'),
  birth: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, 'YYYY-MM-DD 형식'),
  phoneNum: z.string().min(1, '전화번호를 입력하세요'),
})
type FormData = z.infer<typeof schema>

export function SellerSignup() {
  const navigate = useNavigate()
  const {
    register,
    handleSubmit,
    formState: { errors },
    getValues,
  } = useForm<FormData>({ resolver: zodResolver(schema) })

  const signupMutation = useMutation({
    mutationFn: signupSeller,
    onSuccess: () => {
      const { email } = getValues()
      navigate(`/seller/signup/verify?email=${encodeURIComponent(email)}`)
    },
  })

  return (
    <section>
      <h1>회원가입 (SELLER)</h1>
      <form onSubmit={handleSubmit((data) => signupMutation.mutate(data))} className="form">
        <label>
          이메일
          <input type="email" autoComplete="email" {...register('email')} />
          {errors.email && <small className="err">{errors.email.message}</small>}
        </label>
        <label>
          이름 / 상호
          <input autoComplete="name" {...register('name')} />
          {errors.name && <small className="err">{errors.name.message}</small>}
        </label>
        <label>
          비밀번호 (8자 이상)
          <input type="password" autoComplete="new-password" {...register('password')} />
          {errors.password && <small className="err">{errors.password.message}</small>}
        </label>
        <label>
          생년월일 (YYYY-MM-DD)
          <input placeholder="1990-01-01" {...register('birth')} />
          {errors.birth && <small className="err">{errors.birth.message}</small>}
        </label>
        <label>
          전화번호
          <input autoComplete="tel" placeholder="010-1234-5678" {...register('phoneNum')} />
          {errors.phoneNum && <small className="err">{errors.phoneNum.message}</small>}
        </label>
        <button type="submit" disabled={signupMutation.isPending}>
          {signupMutation.isPending ? '가입 중...' : '가입 신청'}
        </button>
        {signupMutation.isError && <p className="err">{extractApiMessage(signupMutation.error)}</p>}
      </form>
      <p>
        이미 가입했나요? <Link to="/seller/login">로그인</Link>
      </p>
    </section>
  )
}
