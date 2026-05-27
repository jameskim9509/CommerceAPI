import { Link, Outlet } from 'react-router-dom'

export function SellerLayout() {
  return (
    <div>
      <header>
        <nav>
          <Link to="/seller/products">상품 관리</Link>
          {' | '}
          <Link to="/seller/orders">주문 관리</Link>
          {' | '}
          <Link to="/customer">← CUSTOMER 모드</Link>
        </nav>
      </header>
      <main>
        <Outlet />
      </main>
    </div>
  )
}
