import type { ReadonlyURLSearchParams } from 'next/navigation'

const MAP_FILTER_KEYS = ['query', 'district', 'category', 'creatorId'] as const
const FILTERED_EXPLORATION_PATHS = new Set(['/restaurants', '/map'])

/**
 * 맛집 목록과 지도 사이를 이동할 때 공개 탐색 조건만 이어 간다.
 * 페이지 번호와 목록 크기는 지도 API 계약에 없으므로 전달하지 않는다.
 */
export function buildMapNavigationHref(
  pathname: string,
  currentSearchParams: Pick<ReadonlyURLSearchParams, 'get'>,
): string {
  if (!FILTERED_EXPLORATION_PATHS.has(pathname)) {
    return '/map'
  }

  const next = new URLSearchParams()
  for (const key of MAP_FILTER_KEYS) {
    const value = currentSearchParams.get(key)?.trim()
    if (value) {
      next.set(key, value)
    }
  }

  const queryString = next.toString()
  return queryString ? `/map?${queryString}` : '/map'
}
