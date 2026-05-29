import { api } from './client'
import type { OrderDto } from '@/types/dto'

export async function getOrder(orderId: number): Promise<OrderDto> {
  const { data } = await api.get<OrderDto>(`/order/customer/orders/${orderId}`)
  return data
}
