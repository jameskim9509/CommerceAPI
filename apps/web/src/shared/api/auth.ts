import { api } from './client'
import type { SignupInput, SignupOutput, SigninInput } from '@/types/dto'

// nginx /api/user/customer/signup → gateway /user/customer/signup → user-api /customer/signup
export async function signupCustomer(input: SignupInput): Promise<SignupOutput> {
  const { data } = await api.post<SignupOutput>('/user/customer/signup', input)
  return data
}

export async function verifyCustomerEmail(email: string, code: string): Promise<SignupOutput> {
  const { data } = await api.put<SignupOutput>('/user/customer/signup/verify', null, {
    params: { email, code },
  })
  return data
}

// 응답 body 가 JWT 문자열
export async function loginCustomer(input: SigninInput): Promise<string> {
  const { data } = await api.post<string>('/user/customer/login', input)
  return data
}
