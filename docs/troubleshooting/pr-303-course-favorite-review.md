---
related_documents:
  - ../08-planning/third-expansion-scope-and-terminology.md
  - ../04-product/traceability.md
  - ../05-specs/api-traceability.md
  - ../05-specs/data/data-traceability.md
  - ../04-product/prd/discovery/restaurant-course-recommendation.md
---

# PR #303 리뷰 트러블슈팅: 코스 찜 후보의 권한·범위 추적성

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#303](https://github.com/team-youngkk/masit-on/pull/303) |
| 작성자 | @tjdgns0618 |
| 처리 일자 | 2026-08-24 |
| 범위 | 관리자 세션의 회원 전용 찜 후보 노출 차단, 3차 확장 범위·제품/API/데이터 추적 문서 정합화 |
| 주 문제 유형 | 애플리케이션, 기타(제품 범위·추적성 계약) |
| 기존 기록 | [PR #174 코스 공개 화면](pr-174-course-public-screen-review.md), [PR #171 코스 외부 경계](pr-171-course-route-review.md), [PR #135 개인 컬렉션 소유권](pr-135-personal-collection-review.md)을 확인했다. 기존의 공개 코스·개인 데이터 경계 원칙을 이번 권한·범위 수정에 적용했다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [관리자 찜 후보 노출](https://github.com/team-youngkk/masit-on/pull/303#discussion_r3840334134) | `authenticated`만 확인하지 말고 `session.role === MEMBER`일 때만 찜 후보 영역을 노출 | 애플리케이션 | 수정 필요 | `canUseCourseFavoriteSource`로 MEMBER 세션만 허용하고 ADMIN·익명·인증 불가 상태를 차단했다. | 역할별 순수 함수 회귀 테스트, 타입체크, 프론트 전체 테스트·빌드 통과 |
| [3차 범위·추적 문서 충돌](https://github.com/team-youngkk/masit-on/pull/303#discussion_r3840336546) | 자동 추천 입력 제외 문서와 명시적 찜 후보 선택 기능의 범위를 동기화 | 기타(제품 범위·추적성) | 수정 필요 | 자동 추천·자동 선정을 제외하고, 로그인 회원의 명시적 찜 목록 불러오기만 WS-16 범위로 명시했다. 제품·API·데이터 추적표에 `API-PERSONAL-004` 연결과 비저장 경계를 반영했다. | 관련 문서 diff 검사 및 프론트 전체 테스트·빌드 통과 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: 없음. 관리자에서는 회원 전용 찜 API 호출 버튼이 잘못 노출될 수 있었다.
- 발생 환경: PR #303, Next.js 프론트엔드, 통합 세션에서 `MEMBER`와 `ADMIN` 모두 `authenticated` 상태.
- 재현 조건: `useMemberSession()`이 `authenticated`를 반환하고 `session.role`이 `ADMIN`인 상태로 `/course` 진입.
- 실제 결과: 관리자에게 `/api/me/favorites`를 호출하는 `내 찜에서 찾기` 영역이 렌더링된다.
- 기대 결과: 관리자·익명 사용자는 공개 검색 기반 코스만 사용하고, 일반 회원만 본인 찜 후보를 명시적으로 불러온다.
- 영향 범위: 관리자 화면의 잘못된 회원 기능 노출과 401 안내 오인 가능성, 상위 3차 확장 범위 문서와 코스 PRD의 추적성 충돌.

## 4. 근본 원인

첫 번째 문제의 원인은 UI가 인증 상태만 검사하고 회원 역할을 검사하지 않은 것이다. 통합 인증은 `MEMBER`와 `ADMIN` 모두 `authenticated`로 표현하므로 개인 API 경계와 화면 조건이 달랐다.

두 번째 문제의 원인은 상위 범위 문서가 “찜을 추천 입력으로 사용하지 않는다”는 정책을 자동 추천과 사용자의 명시적 후보 선택으로 구분하지 않은 채 기록하고 있었던 것이다. PR #303은 PRD·흐름·와이어프레임을 먼저 확장해 상위 scope·추적표와 문장상 충돌이 생겼다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| `useMemberSession()`의 상태·세션 역할과 개인 찜 API 계약 확인 | `ADMIN`도 `authenticated`지만 `/api/me/**`는 일반 회원 경계임을 확인 | 역할 조건을 순수 함수로 분리하고 회귀 테스트 추가 |
| 3차 확장 scope 3.3절과 코스 PRD 6.3절 비교 | 자동 추천 제외와 명시적 수동 선택이 문서에서 구분되지 않음 | 자동 추천 제외는 유지하고 명시적 찜 불러오기를 WS-16 범위로 명시 |
| 제품·API·데이터 추적표의 코스 행 확인 | 개인 찜 API 연결과 인증 경계가 코스 행에 누락 | `API-PERSONAL-004` 및 기존 `favorite` 비저장 조합을 추적표에 추가 |

## 6. 최종 해결

- 변경 내용: MEMBER 역할만 코스 찜 후보 영역을 사용하도록 조건을 좁혔다. 상위 범위 문서에는 자동 추천과 명시적 후보 선택을 구분하고, 제품/API/데이터 추적표에 기존 개인 찜 API 재사용을 연결했다.
- 선택 이유: 개인 API의 인증 경계를 UI에서 먼저 보장하면서, 사용자가 요청한 명시적 찜 후보 선택 기능은 유지할 수 있다.
- 변경 파일: `frontend/app/course/CourseScreen.tsx`, `frontend/lib/course/course-selection.ts`, `frontend/lib/course/course-selection.test.ts`, `docs/08-planning/third-expansion-scope-and-terminology.md`, `docs/04-product/traceability.md`, `docs/05-specs/api-traceability.md`, `docs/05-specs/data/data-traceability.md`
- 고려한 대안: 찜 후보 기능 전체 제거는 상위 범위 충돌은 없애지만 사용자 요청과 PR의 목적을 폐기하므로 채택하지 않았다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `node --test lib/course/course-selection.test.ts lib/course/course-favorites-api.test.ts` | 통과 | 19개 테스트, MEMBER만 허용·ADMIN 차단·기존 찜 후보 선택 경로 확인 |
| `npm.cmd run typecheck` | 통과 | 역할 조건과 문서 변경에 대응하는 프론트 타입 검사 |
| `npm.cmd test` | 통과 | 전체 프론트 테스트 319개 |
| `npm.cmd run build` | 통과 | Next.js 프로덕션 빌드 및 32개 정적 페이지 생성 |
| `git diff --check` | 통과 | 공백·EOF 오류 없음 |

## 8. 재발 방지 및 다음 확인

- 재발 방지: 회원 역할 조건을 순수 함수로 고정하고 MEMBER·ADMIN·익명·인증 불가 시나리오를 회귀 테스트에 추가했다. 범위 문서와 세 추적표에 명시적 찜 후보 선택의 근거를 연결했다.
- 다음 확인: 실제 관리자 세션 브라우저에서 찜 후보 영역이 숨겨지는지, 일반 회원 세션에서 영역이 노출되는지 제한 공개 브라우저 인수 시 확인한다. 담당자는 WS-16 리뷰어이며 PR #303 승인 전 확인한다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| 관리자 세션의 회원 전용 찜 영역 노출률 | 측정 없음 | 관리자 세션으로 `/course` 진입 후 영역 노출 여부 확인 | 미측정 | UI 권한 조건 수정으로 0%를 목표로 함 | WS-16 리뷰어, PR #303 승인 전 브라우저 인수 |
| 코스 찜 후보 조회 성능 | 해당 없음 | 기존 개인 찜 API의 계약·운영 지표를 사용하며 코스가 별도 저장·호출을 추가하지 않음 | 해당 없음 | 새 저장·경로 API 호출 지표를 만들지 않음 | 해당 없음 |

## 10. 남은 사항

- 실제 관리자 계정 브라우저 인수는 로컬 제한 환경에서 아직 실행하지 않았다. 코드·회귀 테스트·빌드는 통과했으며 PR 승인 전 확인 대상으로 남긴다.
