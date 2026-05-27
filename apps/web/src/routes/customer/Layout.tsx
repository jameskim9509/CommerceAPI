import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '@/shared/auth/AuthContext'

export function CustomerLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  const onLogout = () => {
    logout()
    navigate('/customer/login')
  }

  const onAuthPage = location.pathname.includes('/login') || location.pathname.includes('/signup')

  return (
    <div>
      <header>
        <nav>
          <Link to="/customer">홈</Link>
          {' | '}
          <Link to="/customer/products">상품</Link>
          {' | '}
          <Link to="/customer/cart">장바구니</Link>
          {' | '}
          <Link to="/customer/orders">주문</Link>
          {' | '}
          <Link to="/seller">SELLER 모드 →</Link>
          <span style={{ float: 'right' }}>
            {user ? (
              <>
                <span>{user.email}</span> <button type="button" onClick={onLogout}>로그아웃</button>
              </>
            ) : onAuthPage ? null : (
              <Link to="/customer/login">로그인</Link>
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
