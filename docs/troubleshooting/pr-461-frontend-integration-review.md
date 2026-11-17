---
related_documents:
  - README.md
  - ../../frontend/DESIGN.md
  - ../06-architecture/dependency-rules.md
  - ../07-adr/platform/web-001-frontend-platform.md
  - ../07-adr/platform/web-002-data-state.md
  - ../07-adr/quality/test-001-automation-strategy.md
  - pr-135-personal-collection-review.md
  - pr-141-admin-curation-review.md
  - pr-460-admin-operations-review.md
  - ../../.codex/skills/troubleshoot-pr-review/SKILL.md
---

# PR #461 리뷰 트러블슈팅: 세션 mutation guard·오류 알림·큐레이션 필드 매핑

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#461 프론트 통합 상태 보강](https://github.com/team-youngkk/masit-on/pull/461) |
| 작성자 | w00lam |
| 처리 일자 | 2026-11-17 |
| 범위 | 미해결 인라인 리뷰 스레드 3건의 재현·수정·검증 |
| 주 문제 유형 | 애플리케이션 |
| 기존 기록 | [PR #135 개인 컬렉션](pr-135-personal-collection-review.md), [PR #141 관리자 큐레이션](pr-141-admin-curation-review.md), [PR #460 관리자 운영 화면](pr-460-admin-operations-review.md)을 확인했다. 컬렉션의 상태·재조회 경계, 관리자 검증 계약과 상태 표현의 공통화 원칙을 이번 수정에 적용했다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [P3 세션 재확인 중 action 초기화](https://github.com/team-youngkk/masit-on/pull/461#discussion_r4595844607) | 진행 중 mutation의 `action` guard를 세션 상태 effect가 초기화하지 않도록 수정 | 애플리케이션 | 수정 필요 | `CollectionDetail`의 세션 전환 effect에서 `setAction(null)`을 제거하고 각 요청의 `finally`에서만 해제하도록 유지했다. | 전체 프론트 테스트 389건, typecheck, build 통과 |
| [P3 Field alert 중복](https://github.com/team-youngkk/masit-on/pull/461#discussion_r4595844614) | 공통 필드 오류의 `role="alert"`를 제거하고 `aria-describedby` 연결을 유지 | 애플리케이션 | 수정 필요 | `Field`의 오류 문단에서 `role="alert"`만 제거했다. 폼 수준 alert와 필드 설명 연결은 유지했다. | 전체 프론트 테스트 389건, typecheck, build 통과 |
| [P3 큐레이션 필드 매핑 추정](https://github.com/team-youngkk/masit-on/pull/461#discussion_r4595844619) | 오류 문구 접두사를 추정하지 말고 검증 결과에서 필드를 직접 받음 | 애플리케이션 | 수정 필요 | `validateCurationTextFields`를 추가해 `{ title?, description? }`를 직접 반환하고 상세 화면이 이를 사용하도록 변경했다. 필드별 회귀 테스트를 추가했다. | 관련 단위 테스트 6건, 전체 테스트 389건, typecheck, build 통과 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: 별도 런타임 오류는 없었으며, 리뷰에서 지적된 상태·접근성·필드 매핑 회귀 가능성이 문제였다.
- 발생 환경: Windows, Node.js 24.18.0, Next.js 프론트, PR `feature/t-455-frontend-regression`, head `8c2e61d3`.
- 재현 조건:
  - 컬렉션 이름 변경·삭제·맛집 제거 요청 중 `MEMBER_SESSION_CHANGED_EVENT`로 세션 재확인이 발생하는 경우
  - 여러 `Field` 오류와 폼 수준 오류가 동시에 표시되는 경우
  - 큐레이션 검증 문구의 접두사가 바뀌거나 제목·설명 오류가 함께 발생하는 경우
- 실제 결과: 세션 전환 effect가 `action`을 먼저 비워 mutation 버튼 guard가 풀릴 수 있었고, 필드 오류마다 alert가 생성되어 폼 alert와 중복될 수 있었다. 큐레이션 상세는 표시 문구의 시작 문자열로 필드를 추정했다.
- 기대 결과: 진행 중 mutation guard는 요청 완료까지 유지되고, 필드 오류는 입력과 `aria-describedby`로 연결되며, 검증 함수가 필드 소유권을 직접 반환해야 한다.
- 영향 범위: 회원 컬렉션 중복 변경 요청, 스크린리더 오류 안내 중복, 관리자 큐레이션 오류 표시와 접근성 연결. API·DB·Route 계약에는 영향이 없다.

## 4. 근본 원인

`CollectionDetail`의 세션 상태 effect가 인증 상태를 정리하면서 진행 중 요청을 나타내는 `action`까지 초기화했다. `action`은 요청별 `finally`에서 해제되어야 하는데 세션 전환과 요청 완료의 생명주기가 섞여 있었다.

공통 `Field` 오류에는 이미 입력의 `aria-describedby`와 `aria-invalid` 연결이 있었지만, 이번 PR에서 모든 필드 오류를 `role="alert"`로 승격했다. `RegistrationFlow` 등 호출 화면의 폼 수준 `role="alert"`와 중복되는 구조였다.

큐레이션 검증 함수는 오류 메시지 배열만 반환했고, 상세 화면이 `제목은`·`설명은`이라는 사용자 문구 접두사로 필드를 역추론했다. 문구 변경이 필드 연결을 깨뜨릴 수 있는 암묵적 결합이었다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| PR #461의 최신 리뷰·미해결 스레드·현재 head 확인 | 미해결 스레드 3건 모두 최신 head `8c2e61d3`에 연결됨 | 세 건을 모두 `수정 필요`로 분류 |
| 기존 `pr-135`, `pr-141`, `pr-460` 기록과 프론트 설계·데이터 상태 ADR 확인 | 컬렉션 상태/재조회, 관리자 검증, 공통 오류 표현의 기존 원칙 확인 | API·Route 변경 없이 화면과 coordination 계층만 수정 |
| `CollectionDetail`의 status effect와 각 mutation `finally` 대조 | `setAction(null)`이 status effect와 요청 완료 양쪽에 존재 | status effect의 초기화만 제거 |
| `Field` 사용처와 폼 수준 `role="alert"` 검색 | 공통 필드와 별도 폼 alert가 함께 사용됨 | Field는 설명 연결만 유지하고 alert는 제거 |
| `validateCurationText` 호출부와 검증 문구 대조 | 상세 화면만 접두사로 필드를 추정하고 생성 화면은 배열 전체를 사용 | 필드별 helper를 추가해 기존 배열 계약은 보존 |

## 6. 최종 해결

- 변경 내용: 세션 상태 전환에서 `CollectionDetail.action`을 초기화하지 않도록 했고, `Field` 오류 문단의 `role="alert"`를 제거했다. `validateCurationTextFields`가 제목·설명 오류를 필드별로 직접 반환하도록 하고 관리자 상세 화면이 그 결과를 사용하도록 변경했다.
- 선택 이유: 진행 중 요청의 guard는 요청 생명주기에만 묶고, 오류 설명은 기존 `aria-describedby` 계약을 유지하며, 검증 결과의 구조를 보존해 사용자 문구 변경에 강하게 만들기 위해서다.
- 변경 파일:
  - `frontend/components/personal/CollectionDetail.tsx`
  - `frontend/components/ui/Field.tsx`
  - `frontend/components/admin/AdminCurationDetail.tsx`
  - `frontend/lib/admin/curations-coordination.ts`
  - `frontend/lib/admin/curations-coordination.test.ts`
  - `docs/troubleshooting/pr-461-frontend-integration-review.md`
  - `docs/troubleshooting/README.md`
- 고려한 대안: Field에 옵션 prop으로 alert를 선택하게 하는 방법은 이번 PR의 공통 오류 정책을 다시 호출부별로 분산시키므로 채택하지 않았다. `validateCurationText`의 배열 반환을 제거하는 방법은 생성 화면의 기존 계약과 테스트를 불필요하게 넓히므로 필드별 helper를 추가하고 기존 함수를 호환 wrapper로 유지했다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `node --test lib/admin/curations-coordination.test.ts` | 통과 | 관련 테스트 6건, 필드별 오류 매핑 회귀 포함 |
| `npm.cmd test` | 통과 | 389 tests passed; pretest 자연어 검색 20건 포함 |
| `npm.cmd run typecheck` | 통과 | TypeScript 검사 통과 |
| `npm.cmd run build` | 통과 | Next.js 컴파일·TypeScript·static pages 32/32. sandbox의 `.next` EPERM 후 escalated 실행에서 통과 |
| `git diff --check` | 통과 | 공백 오류 없음 |
| avoid-ai-design scanner, 변경 UI 18개 파일 | 통과 | P0/P1/P2 0건 |

## 8. 재발 방지 및 다음 확인

- 재발 방지: 필드별 큐레이션 검증 결과 테스트를 추가하고, 컬렉션 요청 guard와 공통 Field의 접근성 연결을 코드 구조상 요청 완료·입력 연결 경계에 고정했다.
- 다음 확인: 원격 head push 후 CI가 이 변경을 통과하는지 확인하고, 각 인라인 스레드에 변경·검증·기록 링크로 답글을 단 뒤 resolve한다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| 진행 중 컬렉션 mutation의 세션 effect 초기화 경로 | 1개 | `CollectionDetail` status effect의 `setAction` 검색 | 0개 | 요청 `finally`만 guard 해제 경로로 유지 | PR #461 후속 리뷰, w00lam |
| Field 오류의 assertive alert 중복 경로 | 공통 Field 1개 + 폼 alert 경로 | `Field.tsx` 및 호출부 role 검색 | 공통 Field 0개 | 입력 설명은 유지하고 폼 요약 alert와 중복되지 않음 | PR #461 후속 리뷰, w00lam |
| 큐레이션 오류 필드 매핑의 문구 추정 | 1개 | 상세 화면의 문자열 접두사 분기 검색 | 0개 | 구조화된 필드별 결과를 직접 사용 | PR #461 후속 리뷰, w00lam |

## 10. 남은 사항

- 컴포넌트 렌더링 테스트 인프라가 없어 실제 마운트 환경의 포커스·스크린리더 동작은 자동 검증하지 못했다. `aria-describedby` 연결과 정적 계약은 유지했고, 브라우저 CUA 환경 제약은 PR 본문에 기록했다.
- 원격 수정 커밋과 답글·스레드 resolve 전까지 PR의 최초 3개 스레드는 미해결 상태로 남는다.
