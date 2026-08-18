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

/*
 * 유한하고 WGS84 범위를 벗어나지 않는 좌표만 통과시킨다. `segments[].path` 원소도 이
 * 검증을 거치므로, 잘못된 좌표(문자열·null·범위 초과)가 CourseRouteMap의 좌표 연산에
 * 그대로 전달돼 지도 effect 전체를 오류 상태로 만드는 것을 여기서 막는다.
 */
function isValidCourseCoordinate(value: unknown): boolean {
  if (typeof value !== 'object' || value === null) {
    return false
  }
  const coordinate = value as Record<string, unknown>
  const { latitude, longitude } = coordinate
  return (
    typeof latitude === 'number' &&
    typeof longitude === 'number' &&
    Number.isFinite(latitude) &&
    Number.isFinite(longitude) &&
    latitude >= -90 &&
    latitude <= 90 &&
    longitude >= -180 &&
    longitude <= 180
  )
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
 * `path` 원소도 좌표 유효성까지 확인한다. 검증하지 않으면 `path: [null]`이나 범위를
 * 벗어난 좌표가 그대로 CourseRouteMap에 전달돼 지도 effect 전체가 오류로 끝난다.
 * `shapeStatus`가 MISSING이면 `path`는 항상 빈 배열이어야 한다는 상태 불변식도
 * 함께 확인한다(BR-COURSE-005).
 */
function isValidCourseSegmentItem(value: unknown): boolean {
  if (typeof value !== 'object' || value === null) {
    return false
  }
  const segment = value as Record<string, unknown>
  if (
    typeof segment.fromRestaurantId !== 'string' ||
    typeof segment.toRestaurantId !== 'string' ||
    typeof segment.distanceMeters !== 'number' ||
    typeof segment.durationSeconds !== 'number' ||
    (segment.shapeStatus !== 'AVAILABLE' && segment.shapeStatus !== 'MISSING') ||
    !Array.isArray(segment.path) ||
    !segment.path.every(isValidCourseCoordinate)
  ) {
    return false
  }
  return segment.shapeStatus === 'AVAILABLE' || segment.path.length === 0
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
