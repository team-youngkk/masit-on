/*
 * GET /api/creators/{creatorId}, /api/creators/{creatorId}/restaurants,
 * /api/creators/{creatorId}/videos 연동 전용 타입·상수·헬퍼.
 * 계약: docs/05-specs/api/detail/creator-detail-api.md
 *       docs/05-specs/api/common/identifier-contract.md
 *       docs/05-specs/api/common/error-contract.md
 *       docs/05-specs/api/common/pagination-contract.md
 *
 * lib/api.ts, lib/restaurants-api.ts와 같은 방식(런타임 환경 변수 API_BASE_URL,
 * 오류 클래스 분리, traceId 추출)을 따르되 이 두 파일은 수정하지 않고 필요한 것만
 * 이 파일에 새로 둔다.
 */

/*
 * SSR은 Next.js 프로세스 안에서 실행되므로 `localhost`는 백엔드가 아니라 자기 자신이다.
 * lib/api.ts, lib/restaurants-api.ts와 같은 방식으로 런타임 환경 변수를 읽고,
 * 기본값만 로컬 개발 기준값으로 둔다.
 */
const API_BASE_URL = process.env.API_BASE_URL ?? 'http://localhost:8080'

/* API-CREATOR-DETAIL-002·003 7절: 두 연결 목록은 기본 페이지 크기 20을 쓴다.
 * 이 화면은 크기 선택 UI를 제공하지 않으므로(와이어프레임 7절) 고정값으로 요청한다. */
const DEFAULT_SIZE = '20'

const FALLBACK_CREATOR_RESTAURANTS_ERROR_MESSAGE =
  '방문 맛집을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.'
const FALLBACK_CREATOR_VIDEOS_ERROR_MESSAGE =
  '근거 영상을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.'

export type CreatorDetail = {
  id: string
  channelName: string
  profileImageUrl: string | null
  description: string | null
  handle: string | null
  channelUrl: string
}

export type CreatorRestaurantItem = {
  id: string
  name: string
  district: string
  category: string
}

export type CreatorVideoItem = {
  id: string
  title: string
  thumbnailUrl: string
  sourceUrl: string
}

export type CreatorListPage = {
  number: number
  size: number
  totalElements: number
  totalPages: number
  hasNext: boolean
}

export type CreatorRestaurantsResponse = {
  items: CreatorRestaurantItem[]
  page: CreatorListPage
}

export type CreatorVideosResponse = {
  items: CreatorVideoItem[]
  page: CreatorListPage
}

type ApiErrorBody = {
  code?: string
  message?: string
  errors?: Array<{ field: string; reason: string }>
  resource?: unknown
  traceId?: string
}

export type FetchCreatorRestaurantsResult =
  | { ok: true; data: CreatorRestaurantsResponse }
  | { ok: false; message: string; traceId?: string }

export type FetchCreatorVideosResult =
  | { ok: true; data: CreatorVideosResponse }
  | { ok: false; message: string; traceId?: string }

/* 존재하지 않음·비공개·삭제·외부 이용 불가를 구분하지 않는 404 응답을 나타낸다. */
export class CreatorNotFoundError extends Error {
  constructor(creatorId: string) {
    super(`유튜버를 찾을 수 없습니다: ${creatorId}`)
    this.name = 'CreatorNotFoundError'
  }
}

/*
 * 식별자 형식 오류(400 INVALID_IDENTIFIER)를 나타낸다. 화면은 이것을 찾을 수 없음과
 * 같게 다룬다. 식별자는 불투명 문자열이라 형식 검증 여부를 알려주면
 * identifier-contract.md의 계약과 어긋난다.
 */
export class CreatorIdentifierInvalidError extends Error {
  constructor(creatorId: string) {
    super(`유튜버 식별자 형식이 올바르지 않습니다: ${creatorId}`)
    this.name = 'CreatorIdentifierInvalidError'
  }
}

/*
 * 기본 정보 제공자 실패(5xx 등)를 나타낸다. 이 상태만 서버에서 원인을 추적할 값이
 * 있으므로 응답의 `traceId`를 화면까지 옮긴다(error-contract.md).
 * API-CREATOR-DETAIL-001 9절: 기본 정보 제공 실패는 상세 전체 실패로 다룬다.
 */
export class CreatorDetailUnavailableError extends Error {
  constructor(
    readonly status: number,
    readonly traceId?: string,
  ) {
    super(`유튜버 상세 조회에 실패했습니다: ${status}`)
    this.name = 'CreatorDetailUnavailableError'
  }
}

/*
 * API-CREATOR-DETAIL-001(GET /api/creators/{creatorId})을 호출한다.
 * 식별자는 불투명 문자열이라 형식을 검증하지 않고 그대로 경로에 전달하되,
 * `#`·`?` 등을 포함한 값이 fetch의 URL 파서에 의해 fragment·query로
 * 해석되지 않도록 경로 세그먼트로 encode한다.
 */
export async function getCreatorDetail(creatorId: string): Promise<CreatorDetail> {
  const response = await fetch(
    `${API_BASE_URL}/api/creators/${encodeURIComponent(creatorId)}`,
    { cache: 'no-store' },
  )

  if (response.status === 404) {
    throw new CreatorNotFoundError(creatorId)
  }

  if (response.status === 400) {
    throw new CreatorIdentifierInvalidError(creatorId)
  }

  if (!response.ok) {
    let traceId: string | undefined
    try {
      traceId = ((await response.json()) as { traceId?: string }).traceId
    } catch {
      // 프록시가 만든 오류 응답처럼 본문이 JSON이 아니면 traceId 없이 안내한다.
    }
    throw new CreatorDetailUnavailableError(response.status, traceId)
  }

  return (await response.json()) as CreatorDetail
}

/*
 * API-CREATOR-DETAIL-002(GET /api/creators/{creatorId}/restaurants)를 호출한다.
 * 기본 정보 조회가 이미 성공한 뒤에만 호출하므로, 이 목록만의 실패(404·400·5xx
 * 포함)는 상세 전체를 실패시키지 않고 이 영역만의 오류 상태로 다룬다
 * (PRD 9절: 한쪽 목록만 실패해도 채널 정보와 나머지 목록은 유지한다).
 */
export async function fetchCreatorRestaurants(
  creatorId: string,
  page: number,
): Promise<FetchCreatorRestaurantsResult> {
  const params = new URLSearchParams({ page: String(page), size: DEFAULT_SIZE })

  let response: Response
  try {
    response = await fetch(
      `${API_BASE_URL}/api/creators/${encodeURIComponent(creatorId)}/restaurants?${params.toString()}`,
      { cache: 'no-store' },
    )
  } catch {
    return { ok: false, message: FALLBACK_CREATOR_RESTAURANTS_ERROR_MESSAGE }
  }

  if (!response.ok) {
    const body = await readErrorBody(response)
    return {
      ok: false,
      message: body?.message ?? FALLBACK_CREATOR_RESTAURANTS_ERROR_MESSAGE,
      traceId: body?.traceId,
    }
  }

  try {
    const data = (await response.json()) as CreatorRestaurantsResponse
    return { ok: true, data }
  } catch {
    return { ok: false, message: FALLBACK_CREATOR_RESTAURANTS_ERROR_MESSAGE }
  }
}

/*
 * API-CREATOR-DETAIL-003(GET /api/creators/{creatorId}/videos)를 호출한다.
 * 방문 맛집 목록과 동일하게 이 목록만의 실패를 상세 전체와 분리해 다룬다.
 */
export async function fetchCreatorVideos(
  creatorId: string,
  page: number,
): Promise<FetchCreatorVideosResult> {
  const params = new URLSearchParams({ page: String(page), size: DEFAULT_SIZE })

  let response: Response
  try {
    response = await fetch(
      `${API_BASE_URL}/api/creators/${encodeURIComponent(creatorId)}/videos?${params.toString()}`,
      { cache: 'no-store' },
    )
  } catch {
    return { ok: false, message: FALLBACK_CREATOR_VIDEOS_ERROR_MESSAGE }
  }

  if (!response.ok) {
    const body = await readErrorBody(response)
    return {
      ok: false,
      message: body?.message ?? FALLBACK_CREATOR_VIDEOS_ERROR_MESSAGE,
      traceId: body?.traceId,
    }
  }

  try {
    const data = (await response.json()) as CreatorVideosResponse
    return { ok: true, data }
  } catch {
    return { ok: false, message: FALLBACK_CREATOR_VIDEOS_ERROR_MESSAGE }
  }
}

async function readErrorBody(response: Response): Promise<ApiErrorBody | null> {
  try {
    return (await response.json()) as ApiErrorBody
  } catch {
    return null
  }
}

/*
 * 화면 쿼리 파라미터(restaurantsPage·videosPage)를 1-base 정수 페이지 값으로
 * 정규화한다. 값이 없거나 정수가 아니거나 1보다 작으면 첫 페이지로 취급해
 * 잘못된 값을 그대로 API에 보내 400을 유발하지 않는다.
 */
/*
 * 백엔드 page는 Java int로 파싱되므로 int 범위를 넘는 값은 400을 받는다. 여기서
 * 상한까지 막지 않으면 `?restaurantsPage=9999999999`가 첫 페이지가 아니라 오류
 * 상태로 렌더된다.
 */
const MAX_PAGE = 2147483647

export function parsePageParam(value: string | undefined): number {
  const parsed = Number(value)
  if (!Number.isSafeInteger(parsed) || parsed < 1 || parsed > MAX_PAGE) {
    return 1
  }
  return parsed
}

/*
 * 방문 맛집·근거 영상 페이지 상태를 URL에 함께 담아 반환한다. 두 목록은 서로
 * 독립적인 쿼리 파라미터(restaurantsPage, videosPage)로 관리하므로(PRD 7절),
 * 한 목록의 페이지를 바꿀 때도 항상 다른 목록의 현재 페이지를 그대로 유지해
 * 전달한다.
 */
export function buildCreatorDetailHref(
  creatorId: string,
  pages: { restaurantsPage: number; videosPage: number },
): string {
  const params = new URLSearchParams()
  params.set('restaurantsPage', String(pages.restaurantsPage))
  params.set('videosPage', String(pages.videosPage))
  return `/creators/${encodeURIComponent(creatorId)}?${params.toString()}`
}
