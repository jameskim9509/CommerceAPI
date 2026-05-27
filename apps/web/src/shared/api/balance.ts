import { api } from './client'
import type { ChangeBalanceInput, ChangeBalanceOutput } from '@/types/dto'

export async function changeBalance(input: ChangeBalanceInput): Promise<ChangeBalanceOutput> {
  const { data } = await api.post<ChangeBalanceOutput>('/user/customer/balance', input)
  return data
}
