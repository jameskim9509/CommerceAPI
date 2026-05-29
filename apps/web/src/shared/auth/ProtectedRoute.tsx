import { Navigate, useLocation } from 'react-router-dom'
import { type PropsWithChildren } from 'react'
import { useAuth, type Role } from './AuthContext'

interface Props extends PropsWithChildren {
  role?: Role
  loginPath?: string
}

export function ProtectedRoute({ children, role, loginPath = '/customer/login' }: Props) {
  const { user } = useAuth()
  const location = useLocation()

  if (!user) {
    return <Navigate to={loginPath} state={{ from: location.pathname }} replace />
  }

  if (role && !user.roles.includes(role)) {
    // 권한 불일치 → 모드별 로그인 화면으로 (반대 모드 토큰으로 들어왔을 때)
    return <Navigate to={loginPath} replace />
  }

  return <>{children}</>
}
