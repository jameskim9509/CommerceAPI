const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api'

export type ApiError = {
  status: number
  message: string
}

export async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE_URL}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(init?.headers ?? {}),
    },
  })
  if (!res.ok) {
    const message = await res.text().catch(() => res.statusText)
    throw { status: res.status, message } satisfies ApiError
  }
  if (res.status === 204) return undefined as T
  return res.json() as Promise<T>
}
