import { createContext, useContext, useMemo, useState, type PropsWithChildren } from 'react'

export type Role = 'CUSTOMER' | 'SELLER'

export type AuthUser = {
  email: string
  role: Role
  token: string
}

type AuthContextValue = {
  user: AuthUser | null
  login: (user: AuthUser) => void
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: PropsWithChildren) {
  const [user, setUser] = useState<AuthUser | null>(null)
  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      login: setUser,
      logout: () => setUser(null),
    }),
    [user],
  )
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
