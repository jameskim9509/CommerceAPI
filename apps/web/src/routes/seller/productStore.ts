// SELLER 가 등록한 product id 를 localStorage 에 누적.
// 백엔드에 `GET /seller/product` 목록 API 가 없어, 검색 기반 우회용으로 두는 보조 인덱스.
// 백엔드 API 가 추가되면 제거.

const STORAGE_PREFIX = 'commerce-seller-products'

function key(sellerId: number | undefined): string | null {
  if (sellerId == null) return null
  return `${STORAGE_PREFIX}:${sellerId}`
}

export function getMyProductIds(sellerId: number | undefined): number[] {
  const k = key(sellerId)
  if (!k) return []
  try {
    const raw = localStorage.getItem(k)
    if (!raw) return []
    const arr = JSON.parse(raw) as unknown
    if (!Array.isArray(arr)) return []
    return arr.filter((v): v is number => typeof v === 'number')
  } catch {
    return []
  }
}

export function rememberMyProductId(sellerId: number | undefined, productId: number): void {
  const k = key(sellerId)
  if (!k) return
  const cur = getMyProductIds(sellerId)
  if (cur.includes(productId)) return
  localStorage.setItem(k, JSON.stringify([...cur, productId]))
}

export function forgetMyProductId(sellerId: number | undefined, productId: number): void {
  const k = key(sellerId)
  if (!k) return
  const cur = getMyProductIds(sellerId)
  localStorage.setItem(k, JSON.stringify(cur.filter((id) => id !== productId)))
}
