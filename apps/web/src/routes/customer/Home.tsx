import { Link } from 'react-router-dom'
import { useAuth } from '@/shared/auth/AuthContext'

export function CustomerHome() {
  const { user } = useAuth()
  return (
    <section>
      <h1>고객 홈</h1>
      {user ? (
        <p>
          <strong>{user.email}</strong> 님 환영합니다.{' '}
          <Link to="/customer/products">상품 보러 가기 →</Link>
        </p>
      ) : (
        <p>
          <Link to="/customer/signup">회원가입</Link>
          {' / '}
          <Link to="/customer/login">로그인</Link>
          {' 후 이용하세요.'}
        </p>
      )}
    </section>
  )
}
