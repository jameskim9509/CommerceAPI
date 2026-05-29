import { Link } from 'react-router-dom'

export function SellerOrders() {
  return (
    <section>
      <h1>판매자 주문 관리</h1>
      <p className="muted">
        ⚠️ 백엔드에 SELLER 전용 주문 조회 API (<code>GET /seller/orders</code>) 가 아직
        없어, 이 화면은 placeholder 입니다.
      </p>
      <p className="muted">
        현재는 CUSTOMER 가 주문 후{' '}
        <Link to="/customer/orders">CUSTOMER 모드 주문 상세</Link>
        에서 상태(PENDING/PAID/CONFIRMED/FAILED) 를 확인할 수 있습니다.
        <br />
        SELLER 측 주문 흐름은 <code>docs/seller-flow.md</code> §6 의 알려진 한계를
        참고하세요.
      </p>
    </section>
  )
}
