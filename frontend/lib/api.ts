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

  if (!response.ok) {
    throw new Error(`맛집 상세 조회에 실패했습니다: ${response.status}`)
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
