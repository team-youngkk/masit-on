/*
 * `/restaurants` 화면에서 자연어 검색 영역의 재마운트 키를 만든다.
 * 계약: docs/04-product/prd/discovery/natural-language-restaurant-discovery.md 7절
 *
 * 자연어 검색 영역은 `'use client'` 모듈이므로 서버 컴포넌트가 그 안의 함수를 호출할 수 없다.
 * 키는 서버에서 계산해 prop으로 넘겨야 하므로 클라이언트 경계 밖의 모듈에 둔다.
 */

/* URL이 소유한 직접 필터의 동일성 키. 값이 바뀌면 자연어 검색 영역을 재마운트해 문장·태그·결과를
 * 초기화한다. `tags`는 화면이 소유한 상태라 키에 넣지 않고, `page`·`size`는 목록 페이지 이동이라
 * 초기화 대상이 아니다. */
export function naturalLanguageFiltersKey(filters: {
  query: string | null
  district: string | null
  category: string | null
  creatorId: string | null
}): string {
  return JSON.stringify([
    filters.query,
    filters.district,
    filters.category,
    filters.creatorId,
  ])
}
