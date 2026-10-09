import type { ReadonlyURLSearchParams } from 'next/navigation'

const MAP_FILTER_KEYS = ['query', 'district', 'category', 'creatorId'] as const
const FILTERED_EXPLORATION_PATHS = new Set(['/restaurants', '/map'])

type MapNavigationSearchParams = Pick<ReadonlyURLSearchParams, 'get'> &
  Partial<Pick<ReadonlyURLSearchParams, 'getAll'>>

export function getMapUnsupportedRegionCode(
  value: string | string[] | undefined,
): string | null {
  const values = Array.isArray(value) ? value : [value]
  const regionCode = values.find((candidate) => candidate?.trim())?.trim()
  return regionCode || null
}

function buildMapFilterSearchParams(
  currentSearchParams: Pick<ReadonlyURLSearchParams, 'get'>,
): URLSearchParams {
  const next = new URLSearchParams()
  for (const key of MAP_FILTER_KEYS) {
    const value = currentSearchParams.get(key)?.trim()
    if (value) {
      next.set(key, value)
    }
  }
  return next
}

/**
 * 맛집 목록과 지도 사이를 이동할 때 공개 탐색 조건만 이어 간다.
 * 페이지 번호와 목록 크기는 지도 API 계약에 없으므로 전달하지 않는다.
 */
export function buildMapNavigationHref(
  pathname: string,
  currentSearchParams: MapNavigationSearchParams,
): string | null {
  if (!FILTERED_EXPLORATION_PATHS.has(pathname)) {
    return '/map'
  }

  // 지도는 전국 지역 계층을 아직 해석하지 못하므로, 선택을 누락한 채 이동하지 않는다.
  const regionCodes = currentSearchParams.getAll?.('regionCode') ?? [
    currentSearchParams.get('regionCode') ?? '',
  ]
  if (getMapUnsupportedRegionCode(regionCodes)) return null

  const next = buildMapFilterSearchParams(currentSearchParams)

  const queryString = next.toString()
  return queryString ? `/map?${queryString}` : '/map'
}

/** 지역 필터를 해제한 뒤에도 지도에서 해석 가능한 탐색 조건만 유지한다. */
export function buildMapNavigationHrefAfterRegionClear(
  pathname: string,
  currentSearchParams: MapNavigationSearchParams,
): string {
  const next = buildMapFilterSearchParams(currentSearchParams)
  return buildMapNavigationHref(pathname, next) ?? '/map'
}
