/*
 * GET /api/restaurants/popular 연동 전용 타입·헬퍼.
 * 계약: docs/05-specs/api/discovery/popular-restaurant-api.md
 *       docs/05-specs/api/common/error-contract.md
 */

/*
 * SSR은 Next.js 프로세스 안에서 실행되므로 `localhost`는 백엔드가 아니라 자기 자신이다.
 * lib/restaurants-api.ts와 같은 방식으로 런타임 환경 변수를 읽고, 기본값만 로컬 개발
 * 기준값으로 둔다.
 */
const API_BASE_URL = process.env.API_BASE_URL ?? 'http://localhost:8080'
const FALLBACK_ERROR_MESSAGE =
  '인기 맛집을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.'

export type PopularRestaurantItem = {
  rank: number
  restaurantId: string
  name: string
  roadAddress: string
  category: string
  favoriteCount: number
}

export type PopularRestaurantsResponse = {
  items: PopularRestaurantItem[]
}

type ApiErrorBody = {
  code?: string
  message?: string
  errors?: Array<{ field: string; reason: string }>
  resource?: unknown
  traceId?: string
}

export type FetchPopularRestaurantsResult =
  | { ok: true; data: PopularRestaurantsResponse }
  | { ok: false; message: string; traceId?: string }

/* API-POPULAR-001(GET /api/restaurants/popular)을 호출한다. 쿼리 파라미터는 없다. */
export async function fetchPopularRestaurants(): Promise<FetchPopularRestaurantsResult> {
  let response: Response

  try {
    response = await fetch(`${API_BASE_URL}/api/restaurants/popular`, {
      cache: 'no-store',
    })
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
    const data = (await response.json()) as PopularRestaurantsResponse
    return { ok: true, data }
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
