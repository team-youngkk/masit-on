/*
 * 백엔드 API 호출 헬퍼.
 * ADR-WEB-002: 초기 서버 데이터는 Server Component `fetch`로 가져온다.
 * ADR-WEB-003 6.1: 외부 백엔드 API는 `/api` 접두사 아래에 있고
 * 로컬 개발에서는 Next.js와 별도 프로세스로 떠 있으므로 origin을 명시해야 한다.
 * 루트 CLAUDE.md 5절의 로컬 실행 기준값에 맞춰 기본값을 `http://localhost:8080`으로 둔다.
 */
const API_BASE_URL = process.env.API_BASE_URL ?? 'http://localhost:8080'

export type RestaurantAddress = {
  roadAddress: string
  detailAddress: string | null
}

export type RestaurantVisitedCreator = {
  id: string
  channelName: string
  channelUrl: string
}

export type RestaurantVideo = {
  id: string
  title: string
  thumbnailUrl: string
  channelName: string
  sourceUrl: string
}

export type RestaurantContentStatus = 'AVAILABLE' | 'TEMPORARILY_UNAVAILABLE'

export type RestaurantDetail = {
  id: string
  name: string
  category: string
  address: RestaurantAddress
  phoneNumber: string
  kakaoPlaceUrl: string
  contentStatus: RestaurantContentStatus
  visitedBy: RestaurantVisitedCreator[]
  videos: RestaurantVideo[]
}

/* 존재하지 않음·비공개·삭제를 구분하지 않는 404 응답을 나타낸다. */
export class RestaurantNotFoundError extends Error {
  constructor(restaurantId: string) {
    super(`맛집을 찾을 수 없습니다: ${restaurantId}`)
    this.name = 'RestaurantNotFoundError'
  }
}

/*
 * 식별자 형식 오류(400 INVALID_IDENTIFIER)를 나타낸다. 화면은 이것을 찾을 수 없음과
 * 같게 다룬다. 사용자가 잘못된 주소를 열었을 때 볼 것은 그 자리에 맛집이 없다는
 * 사실이고, 형식 검증 여부를 알려주면 식별자를 불투명 문자열로 두는 계약
 * (identifier-contract.md)과도 어긋난다. 일시적 조회 실패와는 다른 상태다.
 */
export class RestaurantIdentifierInvalidError extends Error {
  constructor(restaurantId: string) {
    super(`맛집 식별자 형식이 올바르지 않습니다: ${restaurantId}`)
    this.name = 'RestaurantIdentifierInvalidError'
  }
}

/*
 * 기본 정보 제공자 실패(5xx 등)를 나타낸다. 이 상태만 서버에서 원인을 추적할 값이
 * 있으므로 응답의 `traceId`를 화면까지 옮긴다(error-contract.md).
 */
export class RestaurantDetailUnavailableError extends Error {
  constructor(
    readonly status: number,
    readonly traceId?: string,
  ) {
    super(`맛집 상세 조회에 실패했습니다: ${status}`)
    this.name = 'RestaurantDetailUnavailableError'
  }
}

/*
 * API-DETAIL-001(GET /api/restaurants/{restaurantId})을 호출한다.
 * 식별자는 불투명 문자열이라 형식을 검증하지 않고 그대로 경로에 전달하되,
 * `#`·`?` 등을 포함한 값이 fetch의 URL 파서에 의해 fragment·query로
 * 해석되지 않도록 경로 세그먼트로 encode한다.
 */
export async function getRestaurantDetail(
  restaurantId: string,
): Promise<RestaurantDetail> {
  const response = await fetch(
    `${API_BASE_URL}/api/restaurants/${encodeURIComponent(restaurantId)}`,
    { cache: 'no-store' },
  )

  if (response.status === 404) {
    throw new RestaurantNotFoundError(restaurantId)
  }

  if (response.status === 400) {
    throw new RestaurantIdentifierInvalidError(restaurantId)
  }

  if (!response.ok) {
    let traceId: string | undefined
    try {
      traceId = ((await response.json()) as { traceId?: string }).traceId
    } catch {
      // 프록시가 만든 오류 응답처럼 본문이 JSON이 아니면 traceId 없이 안내한다.
    }
    throw new RestaurantDetailUnavailableError(response.status, traceId)
  }

  return (await response.json()) as RestaurantDetail
}

/*
 * 관리자가 등록한 외부 URL(카카오 장소, 채널, 영상 원본)을 클릭 가능한 링크로
 * 렌더링하기 전 최소한의 방어적 확인. `javascript:` 등 위험한 scheme이
 * 그대로 `href`에 들어가 클릭 시 실행되는 것을 막는다.
 */
export function isSafeHttpUrl(url: string): boolean {
  return url.startsWith('http://') || url.startsWith('https://')
}
