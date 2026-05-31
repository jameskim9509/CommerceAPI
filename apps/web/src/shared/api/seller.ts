import { api } from './client'
import type {
  SignupInput,
  SignupOutput,
  SigninInput,
  AddProductInput,
  AddProductOutput,
  AddProductItemInput,
  UpdateProductInput,
  UpdateProductOutput,
  UpdateProductItemInput,
  UpdateProductItemOutput,
} from '@/types/dto'

// nginx /api/user/seller/signup → gateway /user/seller/signup → user-api /seller/signup
export async function signupSeller(input: SignupInput): Promise<SignupOutput> {
  const { data } = await api.post<SignupOutput>('/user/seller/signup', input)
  return data
}

export async function verifySellerEmail(email: string, code: string): Promise<SignupOutput> {
  const { data } = await api.put<SignupOutput>('/user/seller/signup/verify', null, {
    params: { email, code },
  })
  return data
}

// 응답 body 가 JWT 문자열 (CUSTOMER login 과 동일 패턴)
export async function loginSeller(input: SigninInput): Promise<string> {
  const { data } = await api.post<string>('/user/seller/login', input)
  return data
}

// order-api /seller/product CRUD
// gateway 가 /order/** 를 order-api 로 라우팅하므로 prefix /order 가 붙는다.
export async function addProduct(input: AddProductInput): Promise<AddProductOutput> {
  const { data } = await api.post<AddProductOutput>('/order/seller/product', input)
  return data
}

export async function addProductItem(
  input: AddProductItemInput,
): Promise<AddProductOutput> {
  const { data } = await api.post<AddProductOutput>('/order/seller/product/item', input)
  return data
}

export async function updateProduct(input: UpdateProductInput): Promise<UpdateProductOutput> {
  const { data } = await api.put<UpdateProductOutput>('/order/seller/product', input)
  return data
}

export async function updateProductItem(
  input: UpdateProductItemInput,
): Promise<UpdateProductItemOutput> {
  const { data } = await api.put<UpdateProductItemOutput>('/order/seller/product/item', input)
  return data
}

export async function deleteProduct(id: number): Promise<string> {
  const { data } = await api.delete<string>('/order/seller/product', {
    params: { id },
  })
  return data
}

export async function deleteProductItem(id: number): Promise<string> {
  const { data } = await api.delete<string>('/order/seller/product/item', {
    params: { id },
  })
  return data
}
