/*
 * GET /api/restaurants/map-points 쿼리 파라미터 구성 전용 순수 함수.
 * 계약: docs/05-specs/api/discovery/map-discovery-api.md
 *       docs/05-specs/api/common/coordinate-contract.md
 */

export type MapBounds = {
  south: number
  west: number
  north: number
  east: number
}

export type MapPointsFilters = {
  query?: string
  district?: string
  category?: string
  creatorId?: string
}

/*
 * Kakao SDK가 아직 실제 화면 경계를 만들지 못한 동안(초기 로딩·SDK 오류) 쓰는 대체 영역이다.
 * 사용자의 실제 위치를 추정한 값이 아니라 서울 전역을 대략 덮는 고정 좌표다(ADR-MAP-001 6.6).
 */
export const SEOUL_FALLBACK_BOUNDS: MapBounds = {
  south: 37.42,
  west: 126.76,
  north: 37.7,
  east: 127.18,
}

/*
 * API-MAP-001 4절: south/west/north/east는 필수 단일 값이고 query/district/category/creatorId는
 * 값이 있을 때만 단일 값으로 보낸다. 배열·반복 값·쉼표 목록은 서버가 400으로 거부하므로 만들지 않는다.
 */
export function buildMapPointsSearchParams(
  bounds: MapBounds,
  filters: MapPointsFilters = {},
): URLSearchParams {
  const params = new URLSearchParams()
  params.set('south', String(bounds.south))
  params.set('west', String(bounds.west))
  params.set('north', String(bounds.north))
  params.set('east', String(bounds.east))

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
