// 백엔드 DTO 미러.
// gateway 경로: /api/user/* → user-api, /api/order/* → order-api (nginx 프록시 경유)

// ===== user-api =====
export interface SignupInput {
  email: string
  name: string
  password: string
  birth: string // YYYY-MM-DD (LocalDate)
  phoneNum: string
}

export interface SignupOutput {
  message: string
}

export interface SigninInput {
  email: string
  password: string
}
// POST /customer/login 응답은 body 가 JWT 문자열

export interface ChangeBalanceInput {
  money: number
  message: string
  from: string
}

export interface ChangeBalanceOutput {
  balance: number
}

// ===== order-api =====
export interface ProductItemDto {
  id: number
  name: string
  price: number
  count: number
}

export interface ProductDto {
  id: number
  name: string
  description: string
  productItemList: ProductItemDto[]
}

export interface AddProductCartForm {
  id: number // product id
  sellerId: number
  name: string
  description: string
  productItemList: ProductItemDto[]
}

export interface CartProductItem {
  id: number
  name: string
  count: number
  price: number
}

export interface CartProduct {
  id: number
  sellerId: number
  name: string
  description: string
  productItemList: CartProductItem[]
}

export interface Cart {
  customerId: number
  productList: CartProduct[]
  messages: string[]
}

export type OrderStatus = 'PENDING' | 'PAID' | 'CONFIRMED' | 'FAILED'

export interface OrderItem {
  productItemId: number
  name: string
  count: number
  price: number
}

export interface OrderDto {
  orderId: number
  status: OrderStatus
  totalPrice: number
  failureReason: string | null
  items: OrderItem[]
}
