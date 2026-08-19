---
related_documents:
  - ../05-specs/api/common/error-contract.md
  - ../05-specs/api/admin/ai-video-extraction-api.md
---

# PR #251 리뷰 트러블슈팅: 오류 응답 `details`의 배열 정규화 누락

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | https://github.com/team-youngkk/masit-on/pull/251 |
| 작성자 | 양성훈 (tjdgns0618) |
| 처리 일자 | 2026-08-19 |
| 범위 | 리뷰 스레드 2건 (`frontend/lib/admin/api.ts:65`, `docs/troubleshooting/pr-251-details-array-normalization-review.md:24`) |
| 주 문제 유형 | 애플리케이션 |
| 기존 기록 | 조사 전 `docs/troubleshooting/`에서 `Array.isArray`·`typeof ... === 'object'` 관련 기존 기록을 검색했으나 없음 |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [#discussion_r3811970050](https://github.com/team-youngkk/masit-on/pull/251#discussion_r3811970050) | `typeof details === 'object'`는 배열도 통과시켜 `Record<string, unknown>` 불변식과 PR 본문의 "비객체 안전 처리" 서술이 어긋난다. `!Array.isArray(details)` 조건과 배열 응답 회귀 테스트 요청 | 애플리케이션 | 수정 필요 | `frontend/lib/admin/api.ts`에 `!Array.isArray(details)` 조건 추가, `ai-video-extractions.test.ts`에 배열 `details` 회귀 테스트 추가 | `npm run test`(274/274), `npm run typecheck`, `npx next build` 모두 통과 |
| [#discussion_r3812063021](https://github.com/team-youngkk/masit-on/pull/251#discussion_r3812063021) | 트러블슈팅 기록의 스레드 링크가 유효하지 않은 자리표시자(`#discussion_r0`)라 추적성이 없음 | 기타 (문서 링크 정확성) | 수정 필요 | 실제 리뷰 코멘트 링크(`#discussion_r3811970050`)로 교체 | 문서 diff 확인 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: 없음 (런타임 예외 아님, 타입 불변식·계약 위반)
- 발생 환경: `frontend/lib/admin/api.ts`의 `errorFrom`, Node.js 24 / TypeScript 7 프론트엔드
- 재현 조건: 백엔드가 (계약 위반이든 예상 밖 응답이든) 오류 바디의 `details` 필드에 배열 값을 담아 보내는 경우
- 실제 결과: `details && typeof details === 'object' ? (details as Record<string, unknown>) : {}` 조건에서 `typeof [] === 'object'`가 `true`이므로 배열이 그대로 `AdminApiError.details`에 담긴다. 타입 선언(`Record<string, unknown>`)과 어긋나고, 이를 소비하는 `aiValidationConflictFrom` 등은 `details.blockReason`처럼 프로퍼티 접근을 가정하므로 배열이 들어오면 `undefined`가 나와 조용히 잘못된 판단(`blockReason: null`)으로 이어질 수 있다.
- 기대 결과: `details`가 일반 객체가 아니면(배열 포함) 빈 객체 `{}`로 정규화되어야 한다.
- 영향 범위: `AdminApiError.details`를 소비하는 모든 관리자 화면(현재는 AI 영상 추출 예외 복구 UI)의 방어적 파싱. 백엔드가 계약대로 객체만 보내는 한 실제 운영에서 발생하지 않지만, 계약 밖 응답에 대한 방어 코드로서 불변식을 어겼다.

## 4. 근본 원인

JavaScript의 `typeof` 연산자는 배열을 `'object'`로 분류한다. `errorFrom`의 원래 수정(PR #251 최초 커밋)은 "`details`가 없거나 비객체이면 빈 객체로 대체"라는 의도를 `typeof` 검사만으로 구현했는데, 이 검사는 배열을 걸러내지 못한다. 리뷰어가 지적한 대로 확정적 원인이며 추정이 아니다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| `frontend/lib/admin/api.ts`의 `errorFrom` 조건문 코드 확인 | `typeof details === 'object'` 단독 조건으로 배열 통과 확인 | 리뷰어 지적이 코드로 재현됨을 확인, 수정 필요로 분류 |
| `frontend/lib` 전체에서 `AdminApiError.details`를 소비하는 곳 검색 | `ai-video-extractions.ts`의 `aiValidationConflictFrom`만 소비, 프로퍼티 접근 방식(`details.blockReason`) 확인 | 배열이 들어오면 방어 코드 없이 `undefined`로 흘러가 조용한 오판정으로 이어질 수 있음을 확인 |

## 6. 최종 해결

- 변경 내용: `errorFrom`의 details 정규화 조건에 `!Array.isArray(details)`를 추가해 배열을 명시적으로 걸러낸다. `ai-video-extractions.test.ts`에 `details`가 배열인 422 응답을 받았을 때 `AdminApiError.details`가 `{}`로 정규화되고 `aiValidationConflictFrom`이 `blockReason: null`, `recoveryPaths: []`를 반환하는 회귀 테스트를 추가한다.
- 선택 이유: 최소 변경으로 불변식을 복구할 수 있고, 별도 라이브러리나 범용 정규화 유틸리티를 새로 만들 필요가 없다.
- 변경 파일: `frontend/lib/admin/api.ts`, `frontend/lib/admin/ai-video-extractions.test.ts`
- 고려한 대안: `Object.prototype.toString.call(details) === '[object Object]'`처럼 더 엄격한 판별도 고려했으나, 이 코드베이스의 다른 방어적 파싱 코드(`frontend/lib/course/course-screen-state.ts`)와의 일관성과 가독성을 위해 `typeof` + `Array.isArray` 조합을 유지했다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `npm run test` (frontend) | 통과 | 274/274 (신규 배열 회귀 테스트 포함) |
| `npm run typecheck` (frontend) | 통과 | 오류 없음 |
| `npx next build` (frontend) | 통과 | 전체 라우트 정상 빌드 |

## 8. 재발 방지 및 다음 확인

- 재발 방지: `ai-video-extractions.test.ts`에 배열 `details` 회귀 테스트를 추가했다. 같은 함수를 다시 수정할 때 이 테스트가 회귀를 잡는다.
- 다음 확인: 없음.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| 해당 없음 | — | — | — | 런타임 성능이나 오류율에 영향을 주지 않는 타입 방어 수정이라 비교 지표를 두지 않는다 | — |

## 10. 남은 사항

없음. 스레드 1건 모두 처리했다.
