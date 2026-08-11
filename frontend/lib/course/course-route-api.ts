'use client'

/*
 * POST /api/restaurants/course-routes 연동 전용 fetch 래퍼.
 * 계약: docs/05-specs/api/discovery/restaurant-course-recommendation-api.md
 *       docs/05-specs/api/common/error-contract.md
 * 인증 없이 공개 접근하며 현재 위치·회원 식별자·선택 이력을 요청 본문에 넣지 않는다.
 * 클라이언트 컴포넌트에서 next.config.ts의 `/api` rewrite를 거치는 상대 경로로 호출한다
 * (lib/map/map-points-client.ts와 같은 방식).
 */

import {
  classifyCourseRouteError,
  type CourseErrorBody,
  type CourseRouteOutcome,
  type CourseRouteResult,
} from './course-screen-state.ts'

const FALLBACK_ERROR_MESSAGE =
  '코스 경로를 계산하지 못했습니다. 잠시 후 다시 시도해 주세요.'

function isValidCourseRouteResult(value: unknown): value is CourseRouteResult {
  if (typeof value !== 'object' || value === null) {
    return false
  }
  const body = value as Record<string, unknown>
  return (
    body.status === 'SUCCEEDED' &&
    Array.isArray(body.restaurants) &&
    Array.isArray(body.segments) &&
    typeof body.totalDistanceMeters === 'number' &&
    typeof body.totalDurationSeconds === 'number' &&
    typeof body.generatedAt === 'string' &&
    typeof body.expiresAt === 'string'
  )
}

/*
 * `restaurantIds`는 이미 사용자가 화면에서 고른 순서다. 첫 식별자가 출발점으로
 * 고정된다는 계약(4절)을 지키기 위해 이 함수는 순서를 바꾸지 않고 그대로 전달한다.
 */
export async function requestCourseRoute(
  restaurantIds: string[],
  signal?: AbortSignal,
): Promise<CourseRouteOutcome> {
  let response: Response
  try {
    response = await fetch('/api/restaurants/course-routes', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ restaurantIds }),
      cache: 'no-store',
      signal,
    })
  } catch (err) {
    if (err instanceof DOMException && err.name === 'AbortError') {
      throw err
    }
    return { kind: 'error', message: FALLBACK_ERROR_MESSAGE }
  }

  let body: unknown = null
  try {
    body = await response.json()
  } catch {
    body = null
  }

  if (!response.ok) {
    return classifyCourseRouteError(body as CourseErrorBody | null)
  }

  if (!isValidCourseRouteResult(body)) {
    return { kind: 'error', message: FALLBACK_ERROR_MESSAGE }
  }

  return { kind: 'success', route: body }
}
