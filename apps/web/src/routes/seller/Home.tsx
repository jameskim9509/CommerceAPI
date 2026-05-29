import { Link } from 'react-router-dom'
import { useAuth } from '@/shared/auth/AuthContext'

export function SellerHome() {
  const { user } = useAuth()
  return (
    <section>
      <h1>판매자 홈</h1>
      {user ? (
        <p>
          <strong>{user.email}</strong> 님 환영합니다.{' '}
          <Link to="/seller/products">상품 관리로 가기 →</Link>
        </p>
      ) : (
        <p>
          <Link to="/seller/signup">회원가입</Link>
          {' / '}
          <Link to="/seller/login">로그인</Link>
          {' 후 이용하세요.'}
        </p>
      )}
    </section>
  )
}
