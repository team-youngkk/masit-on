/*
 * GET /api/restaurants/map-points를 Server Component에서 prefetch하기 위한 전용 fetch 래퍼.
 * lib/map/map-points-client.ts(`'use client'`)의 fetchMapPoints는 브라우저 상대경로에
 * 의존해 Node 프로세스(Server Component)에서 그대로 쓸 수 없으므로, lib/restaurants-api.ts와
 * 같은 관례를 따라 API_BASE_URL 절대경로로 같은 응답 상태 분기를 미러링한다(ADR-WEB-002).
 * 계약: docs/05-specs/api/discovery/map-discovery-api.md
 *       docs/05-specs/api/common/error-contract.md
 * ADR-MAP-001 4.4: 지도 뷰포트를 서버에 전달하지 않으므로 로그에 남을 원문 자체가 없다.
 *                   이 파일은 console.* 호출을 두지 않는다.
 */
import type { MapPointsFetchResult } from './map-points-client'
import {
  buildMapPointsSearchParams,
  type MapPointsFilters,
} from './map-points-query'
import { classifyMapPointsResponse, type MapPointsApiResponse } from './map-points-response'
import { parseRetryAfterHeader } from './retry-after'
import { trustedClientForwardingHeaders } from './trusted-client-forwarding'

const API_BASE_URL = process.env.API_BASE_URL ?? 'http://localhost:8080'
const FALLBACK_ERROR_MESSAGE =
  '지도 결과를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.'
const FALLBACK_INVALID_MESSAGE = '지도 영역이나 검색 조건을 처리할 수 없습니다.'
const FALLBACK_RATE_LIMIT_MESSAGE =
  '너무 잦은 지도 조작입니다. 잠시 후 다시 시도해 주세요.'

type ApiErrorBody = {
  message?: string
  traceId?: string
}

export async function fetchMapPointsOnServer(
  filters: MapPointsFilters,
  trustedClientAddress?: string,
): Promise<MapPointsFetchResult> {
  const params = buildMapPointsSearchParams(filters)
  const headers = trustedClientForwardingHeaders(trustedClientAddress)

  let response: Response
  try {
    response = await fetch(
      `${API_BASE_URL}/api/restaurants/map-points?${params.toString()}`,
      { cache: 'no-store', headers },
    )
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
