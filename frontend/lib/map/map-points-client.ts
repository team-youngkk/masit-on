'use client'

/*
 * GET /api/restaurants/map-points 연동 전용 fetch 래퍼.
 * 계약: docs/05-specs/api/discovery/map-discovery-api.md
 *       docs/05-specs/api/common/error-contract.md
 * ADR-MAP-001 6.6: bounds 원문을 로그에 남기지 않는다. 이 파일은 console.* 호출을 두지 않는다.
 */
import {
  buildMapPointsSearchParams,
  type MapBounds,
  type MapPointsFilters,
} from './map-points-query'
import {
  classifyMapPointsResponse,
  type MapPointsApiResponse,
  type MapPointsViewState,
} from './map-points-response'
import { parseRetryAfterHeader } from './retry-after'

const FALLBACK_ERROR_MESSAGE =
  '지도 결과를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.'
const FALLBACK_INVALID_MESSAGE = '지도 영역이나 검색 조건을 처리할 수 없습니다.'
const FALLBACK_RATE_LIMIT_MESSAGE =
  '너무 잦은 지도 조작입니다. 잠시 후 다시 시도해 주세요.'

type ApiErrorBody = {
  message?: string
  traceId?: string
}

export type MapPointsFetchResult =
  | { kind: 'ok'; view: MapPointsViewState }
  | { kind: 'invalid'; message: string; traceId?: string }
  | {
      kind: 'rateLimited'
      retryAvailableAt: number | null
      message: string
      traceId?: string
    }
  | { kind: 'error'; message: string; traceId?: string }

export async function fetchMapPoints(
  bounds: MapBounds,
  filters: MapPointsFilters,
  signal?: AbortSignal,
): Promise<MapPointsFetchResult> {
  const params = buildMapPointsSearchParams(bounds, filters)

  let response: Response
  try {
    response = await fetch(`/api/restaurants/map-points?${params.toString()}`, {
      cache: 'no-store',
      signal,
    })
  } catch {
    return { kind: 'error', message: FALLBACK_ERROR_MESSAGE }
  }

  if (response.status === 429) {
    const body = await readErrorBody(response)
    return {
      kind: 'rateLimited',
      retryAvailableAt: parseRetryAfterHeader(
        response.headers.get('Retry-After'),
        Date.now(),
      ),
      message: body?.message ?? FALLBACK_RATE_LIMIT_MESSAGE,
      traceId: body?.traceId,
    }
  }

  if (response.status === 400) {
    const body = await readErrorBody(response)
    return {
      kind: 'invalid',
      message: body?.message ?? FALLBACK_INVALID_MESSAGE,
      traceId: body?.traceId,
    }
  }

  if (!response.ok) {
    const body = await readErrorBody(response)
    return {
      kind: 'error',
      message: body?.message ?? FALLBACK_ERROR_MESSAGE,
      traceId: body?.traceId,
    }
  }

  try {
    const data = (await response.json()) as MapPointsApiResponse
    return { kind: 'ok', view: classifyMapPointsResponse(data) }
  } catch {
    return { kind: 'error', message: FALLBACK_ERROR_MESSAGE }
  }
}

async function readErrorBody(response: Response): Promise<ApiErrorBody | null> {
  try {
    return (await response.json()) as ApiErrorBody
  } catch {
    return null
  }
}
