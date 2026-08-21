/*
 * GET /api/restaurants 연동 전용 타입·상수·헬퍼.
 * 계약: docs/05-specs/api/discovery/restaurant-discovery-api.md
 *       docs/05-specs/api/common/error-contract.md
 *       docs/05-specs/api/common/pagination-contract.md
 *       docs/05-specs/api/common/filtering-contract.md
 */

/*
 * SSR은 Next.js 프로세스 안에서 실행되므로 `localhost`는 백엔드가 아니라 자기 자신이다.
 * 컨테이너로 배포하면 백엔드가 다른 컨테이너·호스트에 있어 상수로는 도달할 수 없다.
 * lib/api.ts와 같은 방식으로 런타임 환경 변수를 읽고, 기본값만 로컬 개발 기준값으로 둔다.
 */
const API_BASE_URL = process.env.API_BASE_URL ?? 'http://localhost:8080'
const FALLBACK_ERROR_MESSAGE =
  '맛집 목록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.'
const FALLBACK_CREATORS_ERROR_MESSAGE =
  '유튜버 목록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.'

const DEFAULT_PAGE = '1'
const DEFAULT_SIZE = '21'

/* filtering-contract.md 1절 고정 10개 카테고리 */
export const CATEGORY_OPTIONS = [
  '한식',
  '중식',
  '일식',
  '양식',
  '동남아 음식',
  '인도·남아시아 음식',
  '분식',
  '카페·디저트',
  '술집·주점',
  '기타',
] as const

/* docs/05-specs/data/seed-data-plan.md 2절 고정 표시 순서 25개 */
export const DISTRICT_OPTIONS = [
  '종로구',
  '중구',
  '용산구',
  '성동구',
  '광진구',
  '동대문구',
  '중랑구',
  '성북구',
  '강북구',
  '도봉구',
  '노원구',
  '은평구',
  '서대문구',
  '마포구',
  '양천구',
  '강서구',
  '구로구',
  '금천구',
  '영등포구',
  '동작구',
  '관악구',
  '서초구',
  '강남구',
  '송파구',
  '강동구',
] as const

export type RawSearchParams = Record<string, string | string[] | undefined>

export type RestaurantVisitedByCreator = {
  id: string
  channelName: string
}

export type RestaurantListItem = {
  id: string
  name: string
  district: string
  category: string
  visitedBy: RestaurantVisitedByCreator[]
  remainingVisitedByCount: number
}

export type RestaurantListPage = {
  number: number
  size: number
  totalElements: number
  totalPages: number
  hasNext: boolean
}

export type RestaurantListResponse = {
  items: RestaurantListItem[]
  page: RestaurantListPage
}

type ApiErrorBody = {
  code?: string
  message?: string
  errors?: Array<{ field: string; reason: string }>
  resource?: unknown
  traceId?: string
}

export type FetchRestaurantsResult =
  | { ok: true; data: RestaurantListResponse }
  | { ok: false; message: string; traceId?: string }

export type Creator = {
  id: string
  channelName: string
}

export type CreatorListResponse = {
  items: Creator[]
}

export type FetchCreatorsResult =
  | { ok: true; data: CreatorListResponse }
  | { ok: false; message: string; traceId?: string }

/* Next.js 검색 파라미터는 반복 쿼리를 배열로 넘길 수 있다. 첫 값만 사용한다. */
export function toSingleValue(
  value: string | string[] | undefined,
): string | undefined {
  return Array.isArray(value) ? value[0] : value
}

/* 화면 searchParams를 API 쿼리로 변환한다. 빈 값·미지정 조건은 보내지 않는다. */
export function buildApiSearchParams(
  rawParams: RawSearchParams,
): URLSearchParams {
  const params = new URLSearchParams()

  const query = toSingleValue(rawParams.query)?.trim()
  if (query) {
    params.set('query', query)
  }

  const district = toSingleValue(rawParams.district)
  if (district) {
    params.set('district', district)
  }

  const category = toSingleValue(rawParams.category)
  if (category) {
    params.set('category', category)
  }

  const creatorId = toSingleValue(rawParams.creatorId)
  if (creatorId) {
    params.set('creatorId', creatorId)
  }

  const page = toSingleValue(rawParams.page)
  params.set('page', page || DEFAULT_PAGE)

  const size = toSingleValue(rawParams.size)
  params.set('size', size || DEFAULT_SIZE)

  return params
}

export async function fetchRestaurants(
  params: URLSearchParams,
): Promise<FetchRestaurantsResult> {
  let response: Response

  try {
    response = await fetch(
      `${API_BASE_URL}/api/restaurants?${params.toString()}`,
      { cache: 'no-store' },
    )
  } catch {
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
    const data = (await response.json()) as RestaurantListResponse
    return { ok: true, data }
  } catch {
    return { ok: false, message: FALLBACK_ERROR_MESSAGE }
  }
}

/* API-CREATOR-DISCOVERY-001(GET /api/creators)을 호출한다. 쿼리 파라미터는 없다. */
export async function fetchCreators(): Promise<FetchCreatorsResult> {
  let response: Response

  try {
    response = await fetch(`${API_BASE_URL}/api/creators`, {
      cache: 'no-store',
    })
  } catch {
    return { ok: false, message: FALLBACK_CREATORS_ERROR_MESSAGE }
  }

  if (!response.ok) {
    const body = await readErrorBody(response)
    return {
      ok: false,
      message: body?.message ?? FALLBACK_CREATORS_ERROR_MESSAGE,
      traceId: body?.traceId,
    }
  }

  try {
    const data = (await response.json()) as CreatorListResponse
    return { ok: true, data }
  } catch {
    return { ok: false, message: FALLBACK_CREATORS_ERROR_MESSAGE }
  }
}

async function readErrorBody(response: Response): Promise<ApiErrorBody | null> {
  try {
    return (await response.json()) as ApiErrorBody
  } catch {
    return null
  }
}

/* 다른 조건은 유지하고 page만 바꾼 목록 화면 경로를 만든다. */
export function buildRestaurantsHref(
  apiParams: URLSearchParams,
  page: number,
): string {
  const next = new URLSearchParams(apiParams)
  next.set('page', String(page))
  return `/restaurants?${next.toString()}`
}

/* 현재 페이지 주변 최대 5개의 페이지 번호 창을 만든다. */
export function buildPageNumbers(
  current: number,
  totalPages: number,
): number[] {
  if (totalPages <= 0) {
    return []
  }

  const windowSize = 5
  let start = Math.max(1, current - Math.floor(windowSize / 2))
  const end = Math.min(totalPages, start + windowSize - 1)
  start = Math.max(1, end - windowSize + 1)

  const pages: number[] = []
  for (let value = start; value <= end; value += 1) {
    pages.push(value)
  }
  return pages
}
