import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '@/shared/auth/AuthContext'

export function SellerLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  const onLogout = () => {
    logout()
    navigate('/seller/login')
  }

  const onAuthPage = location.pathname.includes('/login') || location.pathname.includes('/signup')

  return (
    <div>
      <header>
        <nav>
          <Link to="/seller">홈</Link>
          {' | '}
          <Link to="/seller/products">상품 관리</Link>
          {' | '}
          <Link to="/seller/orders">주문 관리</Link>
          {' | '}
          <Link to="/customer">← CUSTOMER 모드</Link>
          <span style={{ float: 'right' }}>
            {user ? (
              <>
                <span>{user.email}</span> <button type="button" onClick={onLogout}>로그아웃</button>
              </>
            ) : onAuthPage ? null : (
              <Link to="/seller/login">로그인</Link>
            )}
          </span>
        </nav>
      </header>
      <main>
        <Outlet />
      </main>
    </div>
  )
}
