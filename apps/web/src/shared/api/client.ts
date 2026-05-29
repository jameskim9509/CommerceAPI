import axios, { AxiosError, type AxiosInstance } from 'axios'

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api'
export const TOKEN_KEY = 'commerce-token'

export const api: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  headers: { 'Content-Type': 'application/json' },
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

api.interceptors.response.use(
  (res) => res,
  (err: AxiosError) => {
    if (err.response?.status === 401) {
      localStorage.removeItem(TOKEN_KEY)
      const path = window.location.pathname
      // login/signup 화면이 아니면 모드별 로그인 페이지로
      const isAuthPage = path.includes('/login') || path.includes('/signup')
      if (!isAuthPage) {
        if (path.startsWith('/seller')) {
          window.location.href = '/seller/login'
        } else if (path.startsWith('/customer')) {
          window.location.href = '/customer/login'
        }
      }
    }
    return Promise.reject(err)
  },
)

// 백엔드 에러 응답에서 사용자에게 보여줄 메시지 추출
export function extractApiMessage(err: unknown): string {
  if (axios.isAxiosError(err)) {
    const data = err.response?.data
    if (typeof data === 'string') return data
    if (data && typeof data === 'object') {
      const msg = (data as { message?: string }).message
      if (msg) return msg
    }
    return err.message
  }
  return err instanceof Error ? err.message : 'Unknown error'
}
