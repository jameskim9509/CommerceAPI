import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type PropsWithChildren,
} from 'react'
import { TOKEN_KEY } from '../api/client'
import { decodeJwt, isExpired, type JwtPayload } from './jwt'

export type Role = 'CUSTOMER' | 'SELLER'

export interface AuthUser {
  email: string
  roles: Role[]
  id?: number
}

interface AuthContextValue {
  token: string | null
  user: AuthUser | null
  login: (token: string) => void
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

function payloadToUser(payload: JwtPayload | null): AuthUser | null {
  if (!payload) return null
  const email = payload.email ?? payload.sub
  if (!email) return null
  const roles = (payload.roles ?? []).map((r) =>
    r.startsWith('ROLE_') ? (r.slice(5) as Role) : (r as Role),
  )
  return { email, roles, id: payload.id }
}

export function AuthProvider({ children }: PropsWithChildren) {
  const [token, setToken] = useState<string | null>(() => localStorage.getItem(TOKEN_KEY))

  // 부팅 시 만료 토큰 정리
  useEffect(() => {
    if (token && isExpired(decodeJwt(token))) {
      localStorage.removeItem(TOKEN_KEY)
      setToken(null)
    }
  }, [token])

  const login = useCallback((nextToken: string) => {
    localStorage.setItem(TOKEN_KEY, nextToken)
    setToken(nextToken)
  }, [])

  const logout = useCallback(() => {
    localStorage.removeItem(TOKEN_KEY)
    setToken(null)
  }, [])

  const user = useMemo(() => payloadToUser(decodeJwt(token ?? '')), [token])

  const value = useMemo<AuthContextValue>(
    () => ({ token, user, login, logout }),
    [token, user, login, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
