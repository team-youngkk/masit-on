---
related_documents:
  - README.md
  - ../05-specs/api/common/identifier-contract.md
  - ../04-product/prd/account/member-authentication.md
  - ../06-architecture/implementation-conventions.md
  - pr-213-natural-language-filter-reset-review.md
  - pr-174-course-public-screen-review.md
  - pr-142-public-curation-review.md
  - pr-100-email-verification-review.md
---

# PR #217 리뷰 트러블슈팅: 사용자·관리자 프론트 공통 템플릿

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#217 사용자·관리자 프론트 공통 템플릿 통일](https://github.com/team-youngkk/masit-on/pull/217) |
| 작성자 | 김인안 (`@inan0226`) |
| 처리 일자 | 2026-08-16 |
| 범위 | 미해결 인라인 리뷰 스레드 10건의 재현·수정·회귀 검증 |
| 주 문제 유형 | 애플리케이션(상태·접근성·표시 계층) |
| 기존 기록 | PR #213의 불투명 `creatorId` 보존, PR #174의 코스 실패 상세 보존, PR #142의 공개 화면 상태, PR #100의 장애별 인증 메시지 구분을 먼저 확인했다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거·검증 |
|---|---|---|---|---|---|
| [관리자 하위 경로 대시보드 활성화](https://github.com/team-youngkk/masit-on/pull/217#discussion_r3789437899) | `/admin` 대시보드가 하위 경로에서 함께 active 되지 않도록 수정 | 애플리케이션 | 수정 필요 | `/admin`은 정확히 일치할 때만 active로 판정 | 관리자 내비게이션 diff·전체 테스트·타입 검사 |
| [로그인 오류 원인 구분](https://github.com/team-youngkk/masit-on/pull/217#discussion_r3790291604) | 자격 증명 실패와 네트워크·서버 장애 메시지를 구분 | 애플리케이션 | 수정 필요 | `401` Response만 자격 증명 안내로 분류하고 `503`·네트워크 등은 재시도 안내 | 인증 helper 경계 테스트·전체 테스트 |
| [인증 메시지 상태 결합](https://github.com/team-youngkk/masit-on/pull/217#discussion_r3790291605) | `message`와 `messageTone`의 분리 상태를 단일 값으로 통합 | 애플리케이션 | 수정 필요 | `{ tone, text } | null` 단일 상태로 성공·오류·초기화를 통합 | MemberAuthForm diff·타입 검사 |
| [AI 후보 신뢰도 배지](https://github.com/team-youngkk/masit-on/pull/217#discussion_r3790291607) | confidence 값에 따라 배지 색상을 구분 | 애플리케이션 | 수정 필요 | 공통 helper를 추가해 `<0.6` danger, `<0.8` warning, 그 이상 success로 표시 | 경계값 6건 테스트·전체 빌드 |
| [대시보드 활성화 중복 보강](https://github.com/team-youngkk/masit-on/pull/217#discussion_r3790291609) | `/admin/participation` 등에서 대시보드가 중복 active 되지 않도록 수정 | 애플리케이션 | 수정 필요 | 위 exact-match 수정으로 함께 해결 | 관리자 내비게이션 diff·전체 테스트 |
| [알림 401 상태 표시 통일](https://github.com/team-youngkk/masit-on/pull/217#discussion_r3790291612) | 목록 조회 중 401도 익명 진입과 같은 StatePanel 사용 | 애플리케이션 | 수정 필요 | `StatePanel`과 로그인 CTA를 사용하는 경고 상태로 통일 | 알림 화면 diff·타입 검사·빌드 |
| [상세 SectionHeader 제목 크기](https://github.com/team-youngkk/masit-on/pull/217#discussion_r3790291615) | 기존 `--font-size-xl` 유지 및 dead CSS 제거 | 애플리케이션 | 수정 필요 | 공통 SectionHeader h2/h3에 `--font-size-xl`을 명시하고 두 상세 화면의 `.sectionTitle` 제거 | CSS diff·프로덕션 빌드 |
| [코스 실패 alert 범위](https://github.com/team-youngkk/masit-on/pull/217#discussion_r3790291616) | 실패 메시지와 확인 대상 맛집 목록을 하나의 alert 맥락에 포함 | 애플리케이션 | 수정 필요 | 바깥 `role="alert"`로 묶고 StatePanel은 presentation 역할로 내려 중첩 live region을 피함 | CourseScreen diff·타입 검사·빌드 |
| [큐레이션 로딩 aria-busy](https://github.com/team-youngkk/masit-on/pull/217#discussion_r3790291618) | 목록·상세 loading에서 `aria-busy`·`aria-live` 복원 | 애플리케이션 | 수정 필요 | 두 loading 컨테이너에 `aria-busy="true" aria-live="polite"` 복원 | 두 loading 파일 diff·프로덕션 빌드 |
| [불투명 creatorId 보존](https://github.com/team-youngkk/masit-on/pull/217#discussion_r3790707470) | 필터 해제 시 유지되는 `creatorId`를 trim하지 않음 | 애플리케이션 | 수정 필요 | 검색어·지역·카테고리만 trim하고 `creatorId`는 원문 유지 | 앞뒤 공백 URL 회귀 테스트·전체 테스트 |

## 3. 문제 현상과 발생 조건

- `/admin`의 부모 경로 판정이 `startsWith('/admin/')`와 겹쳐 하위 관리자 화면에서 두 메뉴가 동시에 active 되었다.
- 로그인 요청의 `catch`가 모든 실패를 자격 증명 오류 문구로 표시해 `503` 또는 네트워크 장애에서도 잘못된 비밀번호를 다시 입력하게 했다.
- 인증 메시지 텍스트와 톤이 별도 상태라 한쪽만 갱신하는 경로가 생기면 이전 톤이 새 메시지에 남을 수 있었다.
- AI 후보·방문 근거 confidence 배지가 값과 무관하게 항상 success였다.
- 알림 목록의 401, 큐레이션 loading, 코스 실패 후속 목록은 공통 화면 상태로 이동하는 과정에서 표시 또는 접근성 신호가 일부 누락되었다.
- `SectionHeader` 전환 뒤 상세 화면의 기존 제목 크기 CSS가 사용되지 않거나 공통 제목 크기가 명시되지 않았다.
- 구조화 필터를 해제할 때 불투명 식별자까지 `trim()`되어 API에서 받은 `creatorId` 원문이 바뀌었다.

영향 범위는 프론트 표시·접근성·클라이언트 URL 상태이며 API·DB 계약과 저장 데이터는 변경하지 않았다.

## 4. 근본 원인

1. 공통 경로 활성화 조건에 부모 메뉴와 자식 메뉴의 의미 차이가 반영되지 않았다.
2. 인증 화면의 HTTP 오류 분류가 로그인 모드에 동일하게 적용되지 않았고, `message`·`messageTone`을 독립 상태로 유지했다. 회원 인증 API 계약상 로그인 자격 증명 오류는 `401 INVALID_CREDENTIALS`, 인증 저장소 장애는 `503 AUTHENTICATION_SERVICE_UNAVAILABLE`이다.
3. 템플릿 통합 과정에서 기존 화면별 raw 상태 마크업과 CSS·ARIA 속성을 공통 컴포넌트로 옮기면서, 기존 시각·접근성 계약을 함께 옮기는 검사가 부족했다.
4. 필터 네 개를 공통 정규화하면서 검색어와 불투명 식별자에 동일한 `trim()`을 적용했다. 식별자 계약은 클라이언트가 외부 식별자를 해석하거나 변형하지 않고 그대로 전달하도록 요구한다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단 |
|---|---|---|
| PR 스레드·PR diff·현재 소스 대조 | 10개 스레드가 모두 현재 코드에 남아 있음을 확인 | 9개 구현 단위로 묶어 수정하되 10개 스레드는 모두 기록 |
| 기존 PR #213·#174·#142·#100 기록과 계약 대조 | `creatorId` 원문 보존, 코스 실패 대상 보존, 공개 상태 통일, 장애별 인증 메시지 구분을 재사용할 수 있음 | 기존 재발 방지 항목을 현재 계약과 일치하는 범위에서 적용 |
| 로그인 API 계약 확인 | `401 INVALID_CREDENTIALS`와 `503 AUTHENTICATION_SERVICE_UNAVAILABLE`가 구분됨 | `401`만 자격 증명 안내로 분류 |
| confidence 관련 API·ADR·PRD 검색 | 화면용 threshold 수치 계약은 없음 | API 계약을 변경하지 않고 `<0.6`/`<0.8`을 UI 표현 정책으로 한정하고 경계 테스트로 고정 |
| `creatorId` 앞뒤 공백을 포함한 URL 생성 | 수정 전 trim으로 원문이 사라지고, 수정 후 URLSearchParams가 인코딩된 원문을 보존 | 회귀 테스트 추가 |
| 최종 프론트 테스트·타입·빌드 | 모두 통과 | 코드 변경을 완료 |

## 6. 최종 해결

- 관리자 대시보드 경로는 exact match로 분리했다.
- 회원 인증 메시지를 단일 상태로 통합하고, 로그인 `401`과 장애를 분리했다.
- AI confidence tone helper와 경계값 테스트를 추가해 두 화면의 표현 정책을 공유했다.
- 알림 unauthorized를 StatePanel로 통일하고, 상세 제목 크기·dead CSS·큐레이션 loading ARIA를 복원했다.
- 코스 실패 영역은 바깥 alert로 목록까지 감싸고 StatePanel의 역할을 presentation으로 지정했다.
- 구조화 필터 중 `query`·`district`·`category`만 정규화하고 `creatorId`는 원문을 유지했다.

변경 파일은 다음과 같다.

- `frontend/components/admin/AdminNavigation.tsx`
- `frontend/components/admin/AiCandidateRegistration.tsx`
- `frontend/components/admin/AiVideoExtractionDetail.tsx`
- `frontend/components/member/MemberAuthForm.tsx`
- `frontend/components/member/member-auth-form-coordination.ts`
- `frontend/components/member/member-auth-form-coordination.test.ts`
- `frontend/components/notification/NotificationListScreen.tsx`
- `frontend/components/ui/PageShell.module.css`
- `frontend/components/ui/StatePanel.tsx`
- `frontend/app/course/CourseScreen.tsx`
- `frontend/app/curations/loading.tsx`
- `frontend/app/curations/[curationId]/loading.tsx`
- `frontend/app/restaurants/[id]/page.module.css`
- `frontend/app/creators/[id]/page.module.css`
- `frontend/lib/admin/ai-confidence.ts`
- `frontend/lib/admin/ai-confidence.test.ts`
- `frontend/lib/restaurants-filter-navigation.ts`
- `frontend/lib/restaurants-filter-navigation.test.ts`
- `frontend/package.json`

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `npm.cmd test` | 통과 | 자연어 17건과 프론트 전체 236건 |
| `npm.cmd run typecheck` | 통과 | TypeScript 타입 검사 |
| `npm.cmd run build` | 통과 | 테스트·타입 검사·Next.js 16.2.11 프로덕션 빌드와 29개 라우트 생성 |
| `git diff --check` | 통과 | 공백·패치 형식 오류 없음 |

검증 환경의 Node.js는 저장소 확정 24.18.0보다 낮은 24.14.0이었다. 테스트·타입 검사·빌드는 통과했지만, Node 24.18.0 CI에서 재확인이 필요하다. 빌드 중 기존 npm audit 경고도 표시되었으나 이번 변경으로 의존성 버전은 변경하지 않았다.

## 8. 재발 방지 및 다음 확인

- 불투명 식별자를 URL·요청에 전달하는 공통 helper를 변경할 때는 값의 정규화 여부를 필드별 계약으로 확인하고 앞뒤 공백 회귀를 유지한다.
- 공통 상태 컴포넌트로 화면을 통합할 때 기존 `role`, `aria-live`, `aria-busy`, 제목 크기를 전후 diff로 대조한다.
- confidence threshold는 현재 API·ADR 계약이 아닌 관리자 UI 표현 정책으로만 유지한다. 자동 확정·등록 판정 규칙으로 확장하지 않는다.
- 원격 PR에 답글·해결 처리를 하려면 이 로컬 변경을 커밋·푸시한 뒤 PR head와 CI 결과를 다시 확인한다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 수정 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| confidence 배지의 값별 표현 정책 테스트 | 0건 | 프론트 테스트 목록과 helper 검색 | 경계값 6건 | 낮음·중간·높음 경계가 자동 검증됨 | PR #217 로컬 검증, 2026-08-16 |
| `creatorId` 원문 보존 회귀 테스트 | 0건 | 필터 navigation 테스트 검색 | 1건 | 앞뒤 공백 보존을 자동 검증 | PR #217 로컬 검증, 2026-08-16 |
| 운영 오류율·응답 시간 | 해당 없음 | 화면 표시·접근성·URL 상태 변경으로 운영 계측 대상 아님 | 해당 없음 | 비교하지 않음 | 해당 없음 |

## 10. 남은 사항

- 코드 수정 커밋 `24c9f9f`와 문서 상태 갱신 커밋 `78acc87`을 PR 브랜치에 푸시했고, 현재 PR head는 `78acc87`이다. 코드 수정 head의 CI [#563](https://github.com/team-youngkk/masit-on/actions/runs/31928570274)가 성공했다.
- 최초 리뷰 스레드 10건은 원인·변경·검증·이 문서 링크를 답글로 남긴 뒤 모두 해결 처리했다.
- 현재 PR 기준으로 남은 사항은 없다.
