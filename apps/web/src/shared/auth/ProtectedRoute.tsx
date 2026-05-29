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
    return <Navigate to="/" replace />
  }

  return <>{children}</>
}
