/*
 * GET /api/restaurants/map-points 쿼리 파라미터 구성 전용 순수 함수.
 * ADR-MAP-001 4.2: south/west/north/east는 서버 계약에 없으므로 이 함수가 만들지 않는다.
 * 계약: docs/05-specs/api/discovery/map-discovery-api.md
 *       docs/05-specs/api/common/coordinate-contract.md
 */

export type MapPointsFilters = {
  query?: string
  district?: string
  category?: string
  creatorId?: string
}

export type MapBounds = {
  south: number
  west: number
  north: number
  east: number
}

/*
 * Kakao 지도의 초기 중심 좌표 계산에만 쓰는 클라이언트 전용 상수다. 사용자의 실제 위치를
 * 추정한 값이 아니라 서울 전역을 대략 덮는 고정 좌표이며, 서버 요청에는 포함하지 않는다
 * (ADR-MAP-001 4.2~4.3).
 */
export const SEOUL_FALLBACK_BOUNDS: MapBounds = {
  south: 37.42,
  west: 126.76,
  north: 37.7,
  east: 127.18,
}

/*
 * API-MAP-001 4절: query/district/category/creatorId는 값이 있을 때만 단일 값으로 보낸다.
 * 배열·반복 값·쉼표 목록은 서버가 400으로 거부하므로 만들지 않는다.
 */
export function buildMapPointsSearchParams(
  filters: MapPointsFilters = {},
): URLSearchParams {
  const params = new URLSearchParams()

  const query = filters.query?.trim()
  if (query) {
    params.set('query', query)
  }
  if (filters.district) {
    params.set('district', filters.district)
  }
  if (filters.category) {
    params.set('category', filters.category)
  }
  if (filters.creatorId) {
    params.set('creatorId', filters.creatorId)
  }

  return params
}

/*
 * MapScreen(client useQuery)과 app/map/page.tsx(server prefetchQuery)가 같은 필터에서
 * 항상 같은 React Query Key를 만들도록 공유하는 단일 정의다. 두 곳이 각자 배열을 손으로
 * 맞추면 하나만 바뀌어도 hydration이 조용히 무시되고 클라이언트가 다시 조회한다 — 이 함수가
 * bounds를 포함하지 않는다는 것도 이 한 곳에서만 보장하면 된다(ADR-MAP-001 4.2~4.3).
 */
export function buildMapPointsQueryKey(filters: MapPointsFilters = {}) {
  return [
    'map-points',
    filters.query ?? '',
    filters.district ?? '',
    filters.category ?? '',
    filters.creatorId ?? '',
  ]
}
