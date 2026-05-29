// JWT payload 디코드 (서명 검증 없음). 라이브러리 추가 회피용 미니멀.
export interface JwtPayload {
  sub?: string
  email?: string
  roles?: string[]
  id?: number
  exp?: number
  iat?: number
  [k: string]: unknown
}

export function decodeJwt(token: string): JwtPayload | null {
  try {
    const payload = token.split('.')[1]
    if (!payload) return null
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/')
    const padded = base64 + '=='.slice(0, (4 - (base64.length % 4)) % 4)
    const decoded = atob(padded)
    const json = decodeURIComponent(
      decoded
        .split('')
        .map((c) => '%' + c.charCodeAt(0).toString(16).padStart(2, '0'))
        .join(''),
    )
    return JSON.parse(json) as JwtPayload
  } catch {
    return null
  }
}

export function isExpired(payload: JwtPayload | null): boolean {
  if (!payload?.exp) return false
  return Date.now() >= payload.exp * 1000
}
