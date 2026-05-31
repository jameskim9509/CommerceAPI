import { api } from './client'
import type { ProductDto } from '@/types/dto'

// nginx /api/order/search/product?name=
export async function searchProductsByName(name: string): Promise<ProductDto[]> {
  const { data } = await api.get<ProductDto[]>('/order/search/product', {
    params: { name },
  })
  return data
}

export async function getProductDetail(productId: number): Promise<ProductDto> {
  const { data } = await api.get<ProductDto>('/order/search/product/detail', {
    params: { productId },
  })
  return data
}

// 백엔드: GET /search/product/list?productId=1&productId=2&...
export async function getProductListByIds(productIds: number[]): Promise<ProductDto[]> {
  if (productIds.length === 0) return []
  const search = new URLSearchParams()
  productIds.forEach((id) => search.append('productId', String(id)))
  const { data } = await api.get<ProductDto[]>(`/order/search/product/list?${search.toString()}`)
  return data
}
