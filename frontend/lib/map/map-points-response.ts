/*
 * GET /api/restaurants/map-points 응답 해석 전용 순수 함수.
 * 계약: docs/05-specs/api/discovery/map-discovery-api.md 4절
 */

export type MapPointItem = {
  id: string
  name: string
  category: string
  addressSummary: string
  coordinate: {
    latitude: number
    longitude: number
  }
  creatorProfileImageUrl?: string | null
}

export type MapPointsResultStatus = 'AVAILABLE' | 'TOO_MANY_RESULTS'

export type MapPointsApiResponse = {
  resultStatus: MapPointsResultStatus
  limit: number
  items: MapPointItem[]
}

export type MapPointsViewState =
  | { kind: 'empty' }
  | { kind: 'tooMany' }
  | { kind: 'results'; items: MapPointItem[] }

/*
 * TOO_MANY_RESULTS는 계약상 항상 빈 items지만, 임의 일부 마커를 절대 표시하지 않기 위해
 * items 값과 무관하게 resultStatus만으로 tooMany를 판정한다.
 */
export function classifyMapPointsResponse(
  response: MapPointsApiResponse,
): MapPointsViewState {
  if (response.resultStatus === 'TOO_MANY_RESULTS') {
    return { kind: 'tooMany' }
  }

  if (response.items.length === 0) {
    return { kind: 'empty' }
  }

  return { kind: 'results', items: response.items }
}
