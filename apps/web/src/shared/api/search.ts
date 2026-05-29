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
