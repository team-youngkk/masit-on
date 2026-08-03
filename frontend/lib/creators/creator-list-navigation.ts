/*
 * 유튜버 상세의 두 연결 목록을 서로 독립된 조회 경계로 유지한다.
 * 계약: docs/05-specs/api/detail/creator-detail-api.md 2절
 *       "한 연결 목록의 페이지 이동이 다른 목록을 다시 요청하거나 페이지 상태를 바꾸지 않는다"
 *       docs/04-product/prd/detail/creator-detail.md 5.2·5.3절
 *
 * Server Component는 searchParams가 바뀌면 페이지 전체를 다시 실행하므로, 링크 기반
 * 페이지 이동으로는 상대 목록의 API 재요청을 막을 수 없다. 그래서 최초 데이터만
 * 서버에서 받고(ADR-WEB-002) 이후 페이지 이동·재시도는 목록별 클라이언트 조회로
 * 처리한다. 요청 경로와 파라미터 병합을 이 모듈에 모아 회귀 테스트로 고정한다.
 */

export type CreatorListKind = 'restaurants' | 'videos'

/* 두 목록 모두 pagination-contract.md의 기본 크기를 쓰고 클라이언트 정렬 입력은 받지 않는다. */
const LIST_PAGE_SIZE = '20'

export type CreatorListPages = {
  restaurantsPage: number
  videosPage: number
}

export function creatorListPageParamName(
  kind: CreatorListKind,
): 'restaurantsPage' | 'videosPage' {
  return kind === 'restaurants' ? 'restaurantsPage' : 'videosPage'
}

/*
 * 브라우저 조회는 next.config.ts의 `/api/:path*` rewrite를 거치므로 상대 경로를 쓴다.
 * 식별자는 불투명 문자열이라 형식을 검증하지 않고 경로 세그먼트로만 encode한다.
 */
export function creatorListRequestPath(
  creatorId: string,
  kind: CreatorListKind,
  page: number,
): string {
  const params = new URLSearchParams({ page: String(page), size: LIST_PAGE_SIZE })
  return `/api/creators/${encodeURIComponent(creatorId)}/${kind}?${params.toString()}`
}

/* 이동한 목록의 페이지만 바꾸고 상대 목록의 페이지 상태는 그대로 유지한다. */
export function withCreatorListPage(
  current: CreatorListPages,
  kind: CreatorListKind,
  page: number,
): CreatorListPages {
  return kind === 'restaurants'
    ? { ...current, restaurantsPage: page }
    : { ...current, videosPage: page }
}

/*
 * 주소창 검색 문자열에서 이동한 목록의 페이지 파라미터만 갱신한다. 화면은 이 값을
 * history.replaceState로 반영해 새로고침·공유 시 페이지가 유지되게 하면서도 서버
 * 재실행으로 상대 목록이 다시 요청되지 않게 한다.
 */
export function nextCreatorListSearch(
  currentSearch: string,
  kind: CreatorListKind,
  page: number,
): string {
  const params = new URLSearchParams(currentSearch)
  params.set(creatorListPageParamName(kind), String(page))
  return params.toString()
}

export type CreatorListLoadResult<TResponse> =
  | { ok: true; data: TResponse }
  | { ok: false; message: string; traceId?: string }

/*
 * 한 목록의 페이지를 조회한다. 실패는 예외로 올리지 않고 이 목록만의 오류 상태로
 * 돌려주어 채널 정보와 상대 목록이 유지되게 한다(PRD 9절).
 */
export async function loadCreatorListPage<TResponse>(
  creatorId: string,
  kind: CreatorListKind,
  page: number,
  fallbackMessage: string,
  fetchImpl: typeof fetch = globalThis.fetch,
): Promise<CreatorListLoadResult<TResponse>> {
  let response: Response
  try {
    response = await fetchImpl(creatorListRequestPath(creatorId, kind, page), {
      cache: 'no-store',
    })
  } catch {
    return { ok: false, message: fallbackMessage }
  }

  if (!response.ok) {
    let body: { message?: string; traceId?: string } | null = null
    try {
      body = (await response.json()) as { message?: string; traceId?: string }
    } catch {
      /* 프록시가 만든 오류 응답처럼 본문이 JSON이 아니면 기본 문구로 안내한다. */
    }
    return {
      ok: false,
      message: body?.message ?? fallbackMessage,
      traceId: body?.traceId,
    }
  }

  try {
    return { ok: true, data: (await response.json()) as TResponse }
  } catch {
    return { ok: false, message: fallbackMessage }
  }
}
