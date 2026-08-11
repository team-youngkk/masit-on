/*
 * POST /api/restaurants/course-routes 응답·오류를 화면 상태로 판정하는 순수 함수.
 * 계약: docs/05-specs/api/discovery/restaurant-course-recommendation-api.md 4~6절
 *       docs/05-specs/api/common/error-contract.md
 * 좌표·거리·부분 실패는 추정하지 않고 서버가 준 값만 그대로 옮긴다.
 */

export type CourseRestaurantRole = 'START' | 'WAYPOINT' | 'DESTINATION'

export type CourseRestaurant = {
  sequence: number
  restaurantId: string
  name: string
  role: CourseRestaurantRole
}

export type CourseSegment = {
  fromRestaurantId: string
  toRestaurantId: string
  distanceMeters: number
  durationSeconds: number
}

export type CourseRouteResult = {
  status: 'SUCCEEDED'
  restaurants: CourseRestaurant[]
  segments: CourseSegment[]
  totalDistanceMeters: number
  totalDurationSeconds: number
  generatedAt: string
  expiresAt: string
}

/* 400/404/422: 선택 자체를 고쳐야 다시 계산할 수 있는 오류. */
export type CourseInvalidCategory =
  | 'INVALID_COURSE_SIZE'
  | 'DUPLICATE_RESTAURANT_IN_COURSE'
  | 'INVALID_IDENTIFIER'
  | 'INVALID_REQUEST'
  | 'RESTAURANT_NOT_FOUND'
  | 'RESTAURANT_NOT_PUBLIC'
  | 'RESTAURANT_COORDINATE_REQUIRED'
  | 'COURSE_DISTANCE_LIMIT_EXCEEDED'

const INVALID_CATEGORIES: ReadonlySet<string> = new Set<CourseInvalidCategory>([
  'INVALID_COURSE_SIZE',
  'DUPLICATE_RESTAURANT_IN_COURSE',
  'INVALID_IDENTIFIER',
  'INVALID_REQUEST',
  'RESTAURANT_NOT_FOUND',
  'RESTAURANT_NOT_PUBLIC',
  'RESTAURANT_COORDINATE_REQUIRED',
  'COURSE_DISTANCE_LIMIT_EXCEEDED',
])

/* 502/429: 선택은 유효했으나 외부 경로 계산이 실패한 오류. */
export type CourseFailureCategory = 'PARTIAL' | 'PROVIDER_UNAVAILABLE' | 'SERVICE_RATE_LIMIT'

const FAILURE_CATEGORIES: ReadonlySet<string> = new Set<CourseFailureCategory>([
  'PARTIAL',
  'PROVIDER_UNAVAILABLE',
  'SERVICE_RATE_LIMIT',
])

export type CourseRouteOutcome =
  | { kind: 'success'; route: CourseRouteResult }
  | {
      kind: 'invalid'
      category: CourseInvalidCategory
      message: string
      traceId?: string
    }
  | {
      kind: 'failure'
      category: CourseFailureCategory
      message: string
      traceId?: string
      retryAllowed: boolean
    }
  | { kind: 'error'; message: string; traceId?: string }

export type CourseErrorBody = {
  code?: string
  message?: string
  traceId?: string
  details?: {
    failureCategory?: string
    retryGuidance?: {
      action?: string
      message?: string
    } | null
  } | null
}

const FALLBACK_ERROR_MESSAGE = '코스 경로를 계산하지 못했습니다. 잠시 후 다시 시도해 주세요.'

/*
 * `details.retryGuidance.action`은 API 계약이 열거값을 확정하지 않은 안내 문자열이다.
 * 현재 백엔드 `RestaurantCourseFailureDetails.of()`는 모든 실패 범주에 "RESELECT_OR_RETRY"
 * 하나만 반환하므로 이 분기의 재시도 비허용 경로는 실제로는 도달하지 않고, 재시도는 사실상
 * 항상 허용된다. 그럼에도 값 이름을 임의로 고정하지 않기 위해 "RETRY"를 포함하는 안내만
 * 재시도 허용으로 보고 그 밖의 값·누락은 재시도를 비활성화하는 이 방어 규칙을 유지한다.
 * 서버가 이후 quota·비용 차단처럼 재시도 불가 action 값을 확정해 내려주기 시작하면 그때
 * 이 분기가 실제로 동작한다.
 */
function resolveRetryAllowed(body: CourseErrorBody | null): boolean {
  const action = body?.details?.retryGuidance?.action
  return typeof action === 'string' && action.toUpperCase().includes('RETRY')
}

export function classifyCourseRouteError(body: CourseErrorBody | null): CourseRouteOutcome {
  const code = body?.code
  if (typeof code === 'string' && INVALID_CATEGORIES.has(code)) {
    return {
      kind: 'invalid',
      category: code as CourseInvalidCategory,
      message: body?.message ?? FALLBACK_ERROR_MESSAGE,
      traceId: body?.traceId,
    }
  }

  const failureCategory = body?.details?.failureCategory
  if (typeof failureCategory === 'string' && FAILURE_CATEGORIES.has(failureCategory)) {
    return {
      kind: 'failure',
      category: failureCategory as CourseFailureCategory,
      message: body?.message ?? FALLBACK_ERROR_MESSAGE,
      traceId: body?.traceId,
      retryAllowed: resolveRetryAllowed(body),
    }
  }

  return {
    kind: 'error',
    message: body?.message ?? FALLBACK_ERROR_MESSAGE,
    traceId: body?.traceId,
  }
}

export function courseInvalidCategoryLabel(category: CourseInvalidCategory): string {
  switch (category) {
    case 'INVALID_COURSE_SIZE':
      return '선택 개수 오류'
    case 'DUPLICATE_RESTAURANT_IN_COURSE':
      return '중복 선택'
    case 'RESTAURANT_NOT_FOUND':
      return '존재하지 않는 맛집'
    case 'RESTAURANT_NOT_PUBLIC':
      return '비공개 맛집 포함'
    case 'RESTAURANT_COORDINATE_REQUIRED':
      return '좌표 없음'
    case 'COURSE_DISTANCE_LIMIT_EXCEEDED':
      return '30km 초과'
    case 'INVALID_IDENTIFIER':
    case 'INVALID_REQUEST':
      return '요청 오류'
    default:
      return '요청 오류'
  }
}

export function courseFailureCategoryLabel(category: CourseFailureCategory): string {
  switch (category) {
    case 'PARTIAL':
      return '부분 실패'
    case 'PROVIDER_UNAVAILABLE':
      return '외부 장애'
    case 'SERVICE_RATE_LIMIT':
      return '일시 제한'
    default:
      return '오류'
  }
}

/* 정수 미터를 소수 첫째 자리 km로 표시한다(예: 4200 -> "4.2km"). */
export function formatCourseDistance(distanceMeters: number): string {
  return `${(distanceMeters / 1000).toFixed(1)}km`
}

/* 정수 초를 반올림한 분으로 표시한다(예: 780 -> "약 13분"). 도착을 보장하는 표현은 쓰지 않는다. */
export function formatCourseDuration(durationSeconds: number): string {
  const minutes = Math.round(durationSeconds / 60)
  return `약 ${minutes}분`
}

/* `expiresAt`을 해석할 수 없으면 만료된 것으로 취급해 오래된 결과를 최신처럼 보여주지 않는다. */
export function isCourseRouteExpired(expiresAt: string, nowMs: number): boolean {
  const expiresAtMs = Date.parse(expiresAt)
  if (Number.isNaN(expiresAtMs)) {
    return true
  }
  return nowMs >= expiresAtMs
}

/*
 * `expiresAt`까지 남은 시간(ms)을 반환한다. 이미 지났거나 해석할 수 없으면 null이다.
 * 화면이 이 값으로 만료 시각에 정확히 맞춰 타이머를 걸어, 15초 주기 폴링과 만료 시각 사이의
 * 간격 동안 만료된 거리·시간이 최신처럼 노출되는 것을 막는다.
 */
export function msUntilCourseRouteExpiry(expiresAt: string, nowMs: number): number | null {
  const expiresAtMs = Date.parse(expiresAt)
  if (Number.isNaN(expiresAtMs)) {
    return null
  }
  const delay = expiresAtMs - nowMs
  return delay > 0 ? delay : null
}

/*
 * 선택 목록이 이전과 실제로 달라졌는지(구성·순서 기준) 판정한다. 이미 선택된 항목을 다시
 * 추가하거나 5개 상한을 넘겨 목록이 그대로 유지되는 경우는 변경으로 보지 않는다. 목록이
 * 실제로 바뀐 경우에만 진행 중인 코스 계산 요청과 이전 결과를 무효화해야 한다.
 */
export function didCourseSelectionChange(
  previous: { id: string }[],
  next: { id: string }[],
): boolean {
  if (previous === next) {
    return false
  }
  if (previous.length !== next.length) {
    return true
  }
  return previous.some((item, index) => item.id !== next[index]?.id)
}
