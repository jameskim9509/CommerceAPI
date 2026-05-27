import { Link, Outlet } from 'react-router-dom'

export function CustomerLayout() {
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
          <Link to="/customer/orders">주문 내역</Link>
          {' | '}
          <Link to="/seller">SELLER 모드 →</Link>
        </nav>
      </header>
      <main>
        <Outlet />
      </main>
    </div>
  )
}
