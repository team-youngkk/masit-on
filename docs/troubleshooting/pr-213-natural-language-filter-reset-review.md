---
related_documents:
  - ../04-product/prd/discovery/natural-language-restaurant-discovery.md
  - ../05-specs/api/discovery/natural-language-restaurant-discovery-api.md
  - ../05-specs/api/discovery/restaurant-discovery-api.md
  - ../05-specs/api/common/error-contract.md
  - pr-176-natural-language-review.md
  - pr-169-natural-language-search-review.md
---

# PR #213 리뷰 트러블슈팅: 자연어 검색 영역의 URL 필터 변경 초기화와 빈 결과 조건 제거 계약

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#213](https://github.com/team-youngkk/masit-on/pull/213) |
| 작성자 | 양성훈 (`@tjdgns0618`) |
| 처리 일자 | 2026-08-14 |
| 범위 | 미해결 인라인 리뷰 스레드 2건(상태 초기화 결함은 리뷰어 2명이 동일 지적, 빈 결과 조건 제거 계약 1건)의 재현·수정·검증 |
| 주 문제 유형 | 애플리케이션 |
| 기존 기록 | [PR #176](pr-176-natural-language-review.md)과 [PR #169](pr-169-natural-language-search-review.md)를 먼저 확인했다. 화면에 opaque Creator ID·태그 코드를 노출하지 않는 결정과 429 재시도 타이머 기준은 이번에도 유지했고, 공통 HTTP helper·stale-request 통일을 하지 않는 판단도 그대로 따랐다. 두 기록에는 URL 필터 변경 시 클라이언트 상태 초기화 항목이 없어 이번에 새로 남긴다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [r3783174005](https://github.com/team-youngkk/masit-on/pull/213#discussion_r3783174005) (김인안, 박진영이 리뷰 본문에서 동일 지적) | URL 기반 필터가 바뀔 때 자연어 문장·태그·결과 상태를 초기화하고 회귀 테스트를 추가 | 애플리케이션 | 수정 필요 | URL이 소유한 직접 필터 4종으로 재마운트 키를 만들어 자연어 검색 영역에 부여하고, 키 계약 회귀 테스트를 추가 | 유튜버 필터 해제 링크 이동 후 이전 태그·문장 잔존을 브라우저에서 재현하고 수정 후 초기화와 `filters.tags: []` 전송을 확인 |
| [r3783255663](https://github.com/team-youngkk/masit-on/pull/213#discussion_r3783255663) (이우람) | 빈 결과의 "조건 제거 제어" 요구가 2.1절 표시 전용 서술·구현과 충돌하므로 제공 여부를 결정하고 문서를 일치시킬 것 | 기타(문서 계약 불일치) | 수정 필요 | 개별 제거 UI를 만들지 않기로 하고, 사용자 흐름 2.2·PRD 빈 결과 상태·와이어프레임 NLS-EMPTY를 문장 수정·직접 필터·태그 변경 안내로 정정 | 구현은 적용 조건을 텍스트로만 렌더링하며 제거 제어가 없다. 세 문서에서 제거 제어 서술이 남지 않았음을 검색으로 확인 |

첫 스레드에서 리뷰가 지목한 트리거(구조화 필터 폼 GET 제출)는 재현되지 않았다. 원인은 같지만 실제 트리거가 다르므로 4절에 구분해 기록한다.

두 번째 스레드는 코드 결함이 아니라 문서 간 완료 기준 불일치다. 개별 제거 UI는 이슈 #202 범위 밖이고 계약이 요구하는 기능도 아니므로 만들지 않고, 이 PR이 2.1절에 새로 쓴 "표시 전용" 서술 쪽으로 나머지 문서를 맞췄다.

## 3. 문제 현상과 발생 조건

- 오류 메시지: 없음. 잘못된 조건이 다음 요청에 재사용되는 상태 잔존 문제다.
- 발생 환경: Windows 11, Node.js 24, Next.js 16.2.11 App Router `next dev`, 브랜치 `fix/natural-language-error-guidance`(head `6bbc7b7`), 백엔드 미기동(자연어 응답은 stub).
- 재현 조건: `/restaurants?creatorId=abc-123`에서 직접 태그를 선택하고 문장을 입력한 뒤, 같은 화면의 `유튜버 필터 해제` 링크(`next/link`)로 이동한다. 목록 페이지 이동 링크도 같은 경로다.
- 실제 결과: 클라이언트 내비게이션이라 컴포넌트가 유지되고 `sentence`·`tags`·이전 결과가 그대로 남았다. 판정용 전역 표식(`window` 값)이 이동 후에도 살아 있어 전체 문서 로드가 아님을 확인했다.
- 기대 결과: 이 PR이 갱신한 [PRD 7절](../04-product/prd/discovery/natural-language-restaurant-discovery.md)에 따라 URL이 소유한 직접 필터가 바뀌면 자연어 영역의 문장·태그 선택과 이전 결과를 초기화해야 한다.
- 영향 범위: 공개 자연어 검색 화면의 요청 조건 정확성. API·DB 계약과 저장 데이터는 바뀌지 않는다.

## 4. 근본 원인

`sentence`와 `tags`를 `useState` 최초 초기값으로만 받고, 이후 `filters` prop 변경을 반영하는 경로가 없었다. 같은 route에서 searchParams만 바뀌는 클라이언트 내비게이션은 React 트리를 유지하므로 서버가 새 `filters`를 내려줘도 컴포넌트 상태는 이전 값을 유지한다.

리뷰가 지목한 구조화 필터 폼 GET 제출은 이 경로가 아니다. 네이티브 `<form method="get">` 제출은 브라우저 전체 문서 이동이라 상태가 이미 초기화된다. 검증에서 폼 제출 후 전역 표식이 사라지고 문장·태그가 비워지는 것을 확인했다. 즉 리뷰의 결함 판단은 유효하지만 트리거는 `next/link` 기반 화면 내 이동(유튜버 필터 해제, 목록 페이지 이동)이다.

같은 조사에서 두 번째 원인을 하나 더 확인했다. 재마운트 키 함수를 `frontend/lib/natural-language-search-api.ts`에 두었더니 이 모듈이 `'use client'`라서 서버 컴포넌트인 `app/restaurants/page.tsx`가 호출할 수 없었다. `tsc --noEmit`과 `next build`는 통과하고 요청 시점에만 `Attempted to call naturalLanguageFiltersKey() from the server but naturalLanguageFiltersKey is on the client.`로 실패한다. 서버 컴포넌트에서 쓸 헬퍼는 클라이언트 경계 밖 모듈에 둬야 한다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| 구조화 필터 폼 `검색` 제출 후 전역 표식·문장·태그 확인 | 표식 소멸, 문장·태그 초기화 | 리뷰가 지목한 트리거는 재현되지 않음. 다른 이동 경로를 탐색 |
| 헤더 `맛집 탐색` 링크로 같은 route 재진입 | 표식 소멸(전체 로드) | 이 경로도 초기화됨. 계속 탐색 |
| 다른 route(`/map`) 링크 이동 | 표식 유지 | 이 앱의 `next/link` 이동은 클라이언트 내비게이션임을 확인 |
| `?creatorId=abc-123`에서 `유튜버 필터 해제` 링크 이동 | 표식 유지, 태그·문장 잔존 | 결함 재현. 이 경로를 수정 대상으로 확정 |
| 키 함수를 `natural-language-search-api.ts`(`'use client'`)에 배치 | 빌드·타입 검사 통과, 런타임 RSC 오류 | 클라이언트 경계 밖 신규 모듈로 분리 |
| 키에 `tags`·`page`·`size` 포함 검토 | 포함 시 태그 선택과 페이지 이동마다 재마운트되어 입력·결과가 사라짐 | URL이 소유한 직접 필터 4종만 키에 포함 |

## 6. 최종 해결

- 변경 내용: URL이 소유한 직접 필터(`query`·`district`·`category`·`creatorId`)로 동일성 키를 만드는 `naturalLanguageFiltersKey`를 클라이언트 경계 밖 모듈에 추가하고, `app/restaurants/page.tsx`가 이 키를 자연어 검색 영역의 `key`로 넘긴다. 필터가 바뀌면 컴포넌트가 재마운트되어 문장·태그·결과·페이지 상태가 함께 초기화된다. PRD 7절 문장을 실제 동작에 맞게 고쳤다.
- 선택 이유: 상태 4종을 각각 동기화하는 effect보다 재마운트가 초기화 범위를 빠뜨릴 수 없고, 계약 변경 없이 화면 소유 범위 안에서 끝난다. 리뷰어가 제시한 두 대안 중 하나이기도 하다.
- 변경 파일:
  - `frontend/lib/natural-language-filters-key.ts` (신규)
  - `frontend/app/restaurants/page.tsx`
  - `frontend/lib/natural-language-search-api.test.ts`
  - `docs/04-product/prd/discovery/natural-language-restaurant-discovery.md`
  - `docs/04-product/user-flows/third-expansion-user-flows.md`
  - `docs/04-product/wireframes/third-expansion-wireframes.md`
- 빈 결과 조건 제거 계약: 개별 제거 UI를 제공하지 않는 쪽으로 결정하고 사용자 흐름 2.2, PRD 7절 빈 결과 행, 와이어프레임 NLS-EMPTY 상자를 문장 수정·직접 필터·태그 변경 안내로 바꿨다. 화면이 실제로 제공하는 복구 수단(문장 수정, 직접 필터·태그 변경, 기존 필터 검색 이동)만 남긴다.
- 고려한 대안: `useEffect`로 `filters` prop 변경을 감시해 상태를 되돌리는 방식은 초기화 대상이 늘어날 때 누락이 생기고, 진행 중 요청 취소와 순서를 직접 관리해야 해서 채택하지 않았다. 태그를 URL 쿼리로 올리는 방식은 목록 API가 태그 1개만 받는 계약([맛집 탐색 API](../05-specs/api/discovery/restaurant-discovery-api.md) 6절)과 어긋나 제외했다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `npm run test:natural-language` | 통과 | 자연어 17건. 재마운트 키가 직접 필터 4종 변경마다 달라지고 `tags` 변경에는 유지됨 |
| `npm test` | 통과 | 기존 프론트 210건 회귀 없음 |
| `npm run build`(`npm test` + `tsc --noEmit` + `next build`) | 통과 | 타입 검사와 프로덕션 빌드 |
| 브라우저 재현(`/restaurants?creatorId=abc-123` → 태그·문장 입력 → `유튜버 필터 해제`) | 통과 | 수정 전 태그·문장 잔존, 수정 후 클라이언트 내비게이션을 유지한 채 문장·태그·결과 초기화 |
| 브라우저 후속 요청 본문 확인 | 통과 | 초기화 후 문장 검색의 `filters.tags`가 `[]`로 전송됨 |
| `grep -rn "조건 제거\|조건 하나\|제거 제어" docs/04-product/` | 통과 | 자연어 탐색 문서 3종에 제거 제어를 요구하는 서술이 남지 않고 "표시 전용" 서술과 일치함 |

## 8. 재발 방지 및 다음 확인

- 재발 방지: 재마운트 키 계약(직접 필터 4종 변경 시 키 변경, `tags` 변경 시 키 유지)을 `natural-language-search-api.test.ts`의 회귀 테스트로 고정했다.
- 재발 방지: 화면 동작을 문서에 새로 쓸 때 같은 문서의 다른 절과 와이어프레임에 남은 반대 서술을 함께 검색해 정리한다. 이번 빈 결과 계약 불일치는 2.1절만 고치고 2.2절을 남겨 발생했다.
- 재발 방지: 서버 컴포넌트가 호출할 헬퍼는 `'use client'` 모듈에 두지 않는다. 이 위반은 `tsc --noEmit`과 `next build`를 통과하고 요청 시점에만 드러나므로, 화면을 실제로 열어 확인한다. 이번에는 헬퍼를 `frontend/lib/natural-language-filters-key.ts`로 분리했다.
- 다음 확인: 목록 페이지 이동(`page`만 변경)이 초기화를 일으키지 않는 동작은 결과 목록이 필요해 백엔드를 띄운 뒤 확인한다. 담당자 양성훈, 시점은 로컬 통합 실행 회차이며 키에 `page`가 없다는 점은 단위 테스트로 확인했다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| 필터 변경 후 이전 조건 재사용 발생 | 1종 재현(유튜버 필터 해제 이동 후 이전 태그·문장 잔존) | 브라우저 재현 절차와 키 계약 단위 테스트 | 재현 0건, 자동 테스트 1건 통과 | 재현 결함 해소 | 양성훈 / PR #213 검증 시점 |
| 운영 오류율·처리 시간 | 해당 없음(화면 상태 초기화 변경이며 운영 계측 대상이 아니다) | 해당 없음 | 해당 없음 | 비교 불가 | 해당 없음 |

## 10. 남은 사항

- 미해결 스레드 없음.
- 태그 선택지가 V4 seed 18종 상수라 런타임에 추가된 활성 태그는 노출되지 않고 `DEPRECATED` 태그는 서버 `INVALID_FIELD_VALUE`로만 걸러진다. 활성 태그 조회 공개 API가 없어 계약 변경 없이 해소할 수 없으므로 PRD에 제약으로 남겼다. 태그 lifecycle 변경 시 선택지 공급 방식을 함께 결정한다. 결정 주체는 API 소유자와 WS-14 담당자다.
- [3차 확장 브라우저 검증 기록](../08-planning/third-expansion-browser-verification.md) 7절의 `/restaurants` Tab 순회 기록은 태그 체크박스 18개가 추가돼 갱신이 필요하다. 해당 문서는 운영 검증 회차 기록이라 이 PR에서 수정하지 않았고 결정 주체는 문서 소유자다.
