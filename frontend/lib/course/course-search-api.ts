'use client'

/*
 * 코스 화면 안에서 맛집을 검색·추가하기 위한 GET /api/restaurants 연동 전용 fetch 래퍼.
 * 계약: docs/05-specs/api/discovery/restaurant-discovery-api.md
 *       docs/05-specs/api/common/pagination-contract.md
 *       docs/05-specs/api/common/error-contract.md
 * `lib/restaurants-api.ts`는 Server Component 전용(API_BASE_URL 절대 경로)이라
 * 클라이언트 컴포넌트인 이 화면은 `lib/map/map-points-client.ts`와 같은 방식으로
 * next.config.ts의 `/api` rewrite를 거치는 상대 경로를 쓴다.
 */

const FALLBACK_ERROR_MESSAGE =
  '맛집 검색 결과를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.'

const SEARCH_PAGE_SIZE = '20'

export type CourseSearchItem = {
  id: string
  name: string
  district: string
  category: string
}

export type CourseSearchFilters = {
  query?: string
  district?: string
  category?: string
}

export type CourseSearchResult =
  | { ok: true; items: CourseSearchItem[] }
  | { ok: false; message: string; traceId?: string }

type ApiErrorBody = {
  message?: string
  traceId?: string
}

/* restaurant-discovery-api.md 5절 쿼리 계약: 빈 값·미지정 조건은 보내지 않는다. */
export function buildCourseSearchParams(
  filters: CourseSearchFilters,
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

  params.set('page', '1')
  params.set('size', SEARCH_PAGE_SIZE)

  return params
}

/*
 * 목록 응답(restaurant-discovery-api.md 5절)에서 코스 선택에 필요한 최소 필드만
 * 남긴다. 형식이 어긋난 항목은 조용히 반영하지 않고 걸러낸다.
 */
export function normalizeCourseSearchItems(rawItems: unknown[]): CourseSearchItem[] {
  const items: CourseSearchItem[] = []
  for (const rawItem of rawItems) {
    if (typeof rawItem !== 'object' || rawItem === null) {
      continue
    }
    const item = rawItem as Record<string, unknown>
    if (
      typeof item.id === 'string' &&
      item.id.length > 0 &&
      typeof item.name === 'string' &&
      item.name.length > 0 &&
      typeof item.district === 'string' &&
      typeof item.category === 'string'
    ) {
      items.push({
        id: item.id,
        name: item.name,
        district: item.district,
        category: item.category,
      })
    }
  }
  return items
}

export async function searchCourseCandidates(
  filters: CourseSearchFilters,
  signal?: AbortSignal,
): Promise<CourseSearchResult> {
  const params = buildCourseSearchParams(filters)

  let response: Response
  try {
    response = await fetch(`/api/restaurants?${params.toString()}`, {
      cache: 'no-store',
      signal,
    })
  } catch (err) {
    if (err instanceof DOMException && err.name === 'AbortError') {
      throw err
    }
    return { ok: false, message: FALLBACK_ERROR_MESSAGE }
  }

  if (!response.ok) {
    const body = await readErrorBody(response)
    return {
      ok: false,
      message: body?.message ?? FALLBACK_ERROR_MESSAGE,
      traceId: body?.traceId,
    }
  }

  try {
    const data = (await response.json()) as { items?: unknown }
    if (!Array.isArray(data.items)) {
      return { ok: false, message: FALLBACK_ERROR_MESSAGE }
    }
    return { ok: true, items: normalizeCourseSearchItems(data.items) }
  } catch {
    return { ok: false, message: FALLBACK_ERROR_MESSAGE }
  }
}

async function readErrorBody(response: Response): Promise<ApiErrorBody | null> {
  try {
    return (await response.json()) as ApiErrorBody
  } catch {
    return null
  }
}
