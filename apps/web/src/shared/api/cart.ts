import { api } from './client'
import type { AddProductCartForm, Cart } from '@/types/dto'

export async function getCart(): Promise<Cart> {
  const { data } = await api.get<Cart>('/order/customer/cart')
  return data
}

export async function addCart(form: AddProductCartForm): Promise<Cart> {
  const { data } = await api.post<Cart>('/order/customer/cart', form)
  return data
}

export async function updateCart(cart: Cart): Promise<Cart> {
  const { data } = await api.put<Cart>('/order/customer/cart', cart)
  return data
}

// ADR-001: Idempotency-Key 헤더로 중복 주문 방지
export async function orderCart(cart: Cart, idempotencyKey: string): Promise<unknown> {
  const { data } = await api.post('/order/customer/cart/order', cart, {
    headers: { 'Idempotency-Key': idempotencyKey },
  })
  return data
}
