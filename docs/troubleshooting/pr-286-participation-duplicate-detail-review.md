---
related_documents:
  - ../01-requirements/functional-requirements.md
  - ../04-product/prd/participation/user-submission-report.md
  - ../05-specs/api/participation/submission-report-api.md
  - ../../.codex/skills/troubleshoot-pr-review/SKILL.md
---

# PR #286 리뷰 트러블슈팅: 신규 접수 중복 응답의 상세 상태 확인 회귀

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#286 제보·신고 접수와 내역 화면 분리](https://github.com/team-youngkk/masit-on/pull/286) |
| 작성자 | w00lam |
| 처리 일자 | 2026-08-22 |
| 범위 | 신규 제보·신고 화면에서 중복 접수 후 기존 요청 상세를 확인하는 흐름 |
| 주 문제 유형 | 애플리케이션 — 프론트엔드 상태와 렌더링 조건 |
| 기존 기록 | [PR #280 페이지네이션 예외 정책 문서 정합성](pr-280-pagination-policy-documentation-review.md), [PR #273 프론트 화면 동기화 리뷰](pr-273-frontend-ui-sync-review.md)를 확인했다. 이번 건은 문서 계약이 아니라 화면 분리 후 상태 렌더링 조건이 누락된 별도 애플리케이션 회귀다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [jinyp01 인라인 의견](https://github.com/team-youngkk/masit-on/pull/286#discussion_r3830914211) | 신규 접수 화면의 중복 응답에서 기존 상세를 노출하거나 내역으로 이동해야 함 | 애플리케이션 | 수정 필요 | `selected` 상세를 화면 종류와 무관하게 렌더링하도록 수정했다. | PRD의 회원 요청 목록·상세 조회 및 API의 중복 `resource.requestId` 계약 대조 |
| [inan0226 재리뷰](https://github.com/team-youngkk/masit-on/pull/286#discussion_r3830941399) | 동일 회귀를 재확인하고 두 중복 오류 코드의 신규 접수 경로를 테스트해야 함 | 애플리케이션 | 수정 필요 | 중복 요청 식별자 추출 함수를 추가하고 제보·신고 오류 코드를 각각 테스트했다. | `npm test`, `npm run typecheck`, `git diff --check` |
| [tjdgns0618 인라인 의견](https://github.com/team-youngkk/masit-on/pull/286#discussion_r3834593081) | `DUPLICATE_OPEN_SUBMISSION`·`DUPLICATE_OPEN_REPORT` 상세 확인 흐름을 복구해야 함 | 애플리케이션 | 수정 필요 | 동일 원인의 중복 스레드로 위 변경과 검증으로 함께 처리했다. | 두 코드에서 기존 `requestId`를 추출하고 신규 화면의 `selected` 렌더 조건을 대조 |
| [inan0226 후속 인라인 의견](https://github.com/team-youngkk/masit-on/pull/286#discussion_r3835862850) | 중복 상세 조회도 탭 전환 토큰과 현재 종류를 확인해 오래된 상세가 새 탭에 표시되지 않도록 하고 경합 회귀 테스트를 추가해야 함 | 애플리케이션 | 수정 필요 | 중복 상세 조회에 `detailRequest.current` 토큰과 현재 `kind` ref 검증을 추가하고, 토큰·종류 불일치 테스트를 추가했다. | `npm test`, `npm run typecheck`, `git diff --check` |

기존 세 의견은 같은 렌더링 회귀를 지적하므로 하나의 원인과 변경으로 묶었고, 후속 스레드는 같은 상세 조회 경로의 비동기 경합을 추가로 다뤘다.

## 3. 문제 현상과 발생 조건

- 오류 메시지: `DUPLICATE_OPEN_SUBMISSION` 또는 `DUPLICATE_OPEN_REPORT`
- 발생 환경: PR #286 `feature/ws-12-participation-ui`, Next.js 클라이언트 화면, 인증된 회원
- 재현 조건: `/me/requests/new`에서 이미 열린 동일 제보·신고를 다시 접수하고 서버가 `409`와 기존 요청 `resource.requestId`를 반환
- 실제 결과: API 오류와 기존 상세 조회는 수행되지만 `selected` 상세가 신규 화면에 렌더링되지 않는다.
- 기대 결과: 신규 화면에서도 기존 요청의 상태·처리 사유·상세 정보를 즉시 확인할 수 있다.
- 영향 범위: 중복 접수 뒤 사용자가 기존 요청의 처리 상태를 확인하지 못하고 내역 화면으로 다시 이동해야 한다.
- 추가 재현 조건: 중복 응답 후 기존 요청 상세 조회가 끝나기 전에 제보·신고 탭을 전환한다.
- 추가 실제 결과: 탭 전환이 기존 `selected`를 비워도 이전 종류의 비동기 응답이 나중에 도착하면 새 탭의 `selected`를 덮어쓸 수 있다.
- 추가 기대 결과: 탭 전환으로 무효화된 이전 상세 응답은 무시되고 새 탭에는 오래된 상세가 표시되지 않는다.

## 4. 근본 원인

화면을 신규 접수(`view === 'new'`)와 내역(`view === 'history'`)으로 분리하면서 중복 오류 처리부는 기존 요청 상세를 `selected`에 저장하도록 남겨 두었지만, 상세 섹션의 렌더 조건을 `view === 'history' && selected`로 제한했다. 상태 저장과 표시 조건이 서로 다른 화면 정책을 사용한 것이 첫 원인이었다. 이후 상세 조회 토큰 보호가 목록에서 상세를 여는 경로에만 적용되고 중복 오류 경로에는 적용되지 않아, `switchTab`이 `selected`를 비운 뒤에도 오래된 Promise가 다시 상태를 채울 수 있는 비동기 경합이 남아 있었다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| PR #286 미해결 인라인 스레드와 최신 리뷰를 확인 | 같은 P1 회귀가 세 메시지로 지적됨 | 하나의 원인으로 묶어 수정 |
| PRD와 API 계약의 회원 상세·중복 오류 응답을 대조 | 회원이 목록·상세에서 상태와 사유를 확인하고, 중복 응답은 기존 `requestId`를 제공함 | 신규 화면에서도 조회한 상세를 노출해야 함 |
| `ParticipationRequestScreen.tsx`의 중복 처리와 상세 렌더 조건을 대조 | 중복 분기에서 `selected`를 설정하지만 신규 화면에서는 렌더되지 않음 | 상세 조건을 `selected` 존재 여부로 변경 |
| 중복 요청 식별자 추출 로직을 별도 함수로 분리 | 제보·신고 코드와 공백 식별자를 독립적으로 검증할 수 있음 | 두 오류 코드의 회귀 테스트 추가 |
| 중복 상세 조회 중 `switchTab`의 상태 변경과 응답 도착 순서를 대조 | 기존 경로는 탭 전환 뒤에도 오래된 상세 응답을 `selected`에 저장할 수 있음 | 요청 토큰과 현재 탭 종류를 함께 검증하고 경합 회귀 테스트 추가 |

## 6. 최종 해결

- 변경 내용: 선택된 상세는 `new`·`history` 화면 모두 렌더링하도록 수정했다. 중복 오류 응답에서 유효한 `requestId`를 추출하는 함수를 추가해 제보·신고 코드를 같은 검증 가능한 경로로 통합했다. 중복 상세 조회에는 요청 토큰을 발급하고 탭 전환 때 갱신되는 현재 `kind` ref와 함께 검증해 오래된 응답을 무시하도록 했다.
- 선택 이유: 기존 API 계약과 상태 저장 흐름을 유지하면서 사용자에게 누락된 표시만 복구하고, 중복 오류 코드가 추가될 때의 식별자 조건을 테스트로 보호하기 위해서다.
- 변경 파일:
  - `frontend/components/participation/ParticipationRequestScreen.tsx`
  - `frontend/lib/member/participation-coordination.ts`
  - `frontend/lib/member/participation.test.ts`
  - `docs/troubleshooting/README.md`
  - `docs/troubleshooting/pr-286-participation-duplicate-detail-review.md`
- 고려한 대안: 중복 응답 뒤 `/me/requests`로 자동 이동하는 방법도 가능하지만, 신규 접수 화면에서 이미 조회한 기존 상세를 즉시 보여 주는 현재 흐름을 보존할 수 있어 선택하지 않았다. 단순히 `selected`를 비우는 방법은 늦게 도착한 응답을 막지 못하므로 토큰과 탭 종류를 함께 검증하는 방식을 선택했다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `cd frontend; npm test` | 통과 | 290개 테스트 통과(기존 289개와 신규 탭 전환 경합 회귀 테스트) |
| `cd frontend; npm run typecheck` | 통과 | 화면·헬퍼 타입 검사 통과 |
| `git diff --check` | 통과 | 공백 오류 없음 확인 |

## 8. 재발 방지 및 다음 확인

- 재발 방지: `DUPLICATE_OPEN_SUBMISSION`과 `DUPLICATE_OPEN_REPORT`의 기존 요청 식별자 추출을 각각 테스트하고, 상세 렌더 조건을 `selected` 상태와 직접 연결했다. 중복 상세 조회는 요청 토큰과 탭 종류를 함께 확인하며, 두 값이 달라지는 경계를 테스트한다.
- 다음 확인: PR 브랜치 반영 후 리뷰어가 중복 응답 중 탭 전환 시 오래된 상세가 표시되지 않는지 재확인한다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| 중복 접수 후 상세 표시 | 코드상 신규 화면 표시 0건 | 두 중복 오류 코드의 상태 저장·렌더 조건 대조 | 확인 예정 | 수정 후 두 코드가 `selected` 상세 렌더 경로를 사용함 | PR #286 재리뷰 시점, WS-12 리뷰어 |
| 운영 오류율 | 해당 없음 — 화면 렌더링 회귀라 배포 전 트래픽 기준값이 없음 | 배포 후 중복 접수 관련 문의·이탈을 별도 수집할 때 동일 기준으로 비교 | 확인 예정 | 운영 데이터 수집 후 판단 | WS-12 운영 확인 |

## 10. 남은 사항

- 없음. 수정 커밋을 PR 브랜치에 반영하고 후속 인라인 스레드에 답글을 단 뒤 해결 처리했다.

