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

function isValidCourseCoordinate(value: unknown): boolean {
  if (typeof value !== 'object' || value === null) {
    return false
  }
  const coordinate = value as Record<string, unknown>
  return typeof coordinate.latitude === 'number' && typeof coordinate.longitude === 'number'
}

/*
 * 지도 마커 표시(FR-COURSE-004)에 좌표가 필수이므로 방문지 항목은 좌표까지 확인한다.
 * `role` 열거값은 서버가 새 역할을 추가해도 화면이 깨지지 않도록 문자열 여부만 확인한다.
 */
function isValidCourseRestaurantItem(value: unknown): boolean {
  if (typeof value !== 'object' || value === null) {
    return false
  }
  const restaurant = value as Record<string, unknown>
  return (
    typeof restaurant.sequence === 'number' &&
    typeof restaurant.restaurantId === 'string' &&
    typeof restaurant.name === 'string' &&
    typeof restaurant.role === 'string' &&
    isValidCourseCoordinate(restaurant.coordinate)
  )
}

/*
 * `shapeStatus`가 MISSING이면 `path`는 빈 배열이다(BR-COURSE-005). 배열 원소 좌표까지는
 * 여기서 검증하지 않고 그리기 단계에서 형상 상태로만 분기한다.
 */
function isValidCourseSegmentItem(value: unknown): boolean {
  if (typeof value !== 'object' || value === null) {
    return false
  }
  const segment = value as Record<string, unknown>
  return (
    typeof segment.fromRestaurantId === 'string' &&
    typeof segment.toRestaurantId === 'string' &&
    typeof segment.distanceMeters === 'number' &&
    typeof segment.durationSeconds === 'number' &&
    (segment.shapeStatus === 'AVAILABLE' || segment.shapeStatus === 'MISSING') &&
    Array.isArray(segment.path)
  )
}

function isValidCourseRouteResult(value: unknown): value is CourseRouteResult {
  if (typeof value !== 'object' || value === null) {
    return false
  }
  const body = value as Record<string, unknown>
  return (
    body.status === 'SUCCEEDED' &&
    Array.isArray(body.restaurants) &&
    body.restaurants.every(isValidCourseRestaurantItem) &&
    Array.isArray(body.segments) &&
    body.segments.every(isValidCourseSegmentItem) &&
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
