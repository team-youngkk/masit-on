---
related_documents:
  - README.md
  - ../04-product/prd/admin/admin-data-management.md
  - ../04-product/prd/admin/ai-video-information-extraction.md
  - ../05-specs/api/admin/ai-video-extraction-api.md
  - ../05-specs/api/admin/restaurant-visit-tags-api.md
  - ../07-adr/platform/web-006-unified-login-rbac-route.md
  - ../07-adr/integration/ai-001-video-extraction-candidate-boundary.md
  - ../../.codex/skills/troubleshoot-pr-review/SKILL.md
---

# PR #460 리뷰 트러블슈팅: 관리자 상태 표현의 범위·정합성 보완

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#460 관리자 운영 화면 디자인 개편](https://github.com/team-youngkk/masit-on/pull/460) |
| 작성자 | w00lam |
| 처리 일자 | 2026-11-16 |
| 범위 | 채널 감시 집계 범위, AI 상태 표현 중복, confidence 임계값·비정상 값 tone, 보충 검증 오류 문구 |
| 주 문제 유형 | 애플리케이션 |
| 기존 기록 | [PR #227 YouTube 채널 감시 상태·관리 화면 후속 반영](pr-227-youtube-channel-watch-review.md), [PR #170 AI 영상 추출 Provider·Webhook 리뷰](pr-170-ai-video-extraction-review.md)를 확인했다. 기존 기록의 상태·외부 연동 계약은 유지하고, 이번에는 관리자 표시 계층만 보완했다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [P2 현재 페이지 집계](https://github.com/team-youngkk/masit-on/pull/460#discussion_r4581326177) | 채널 감시 집계가 현재 페이지 기준임을 화면 문구에 표시 | 애플리케이션 | 수정 필요 | 관리자 안내 문구에 현재 페이지 기준을 명시했다. | `npm.cmd test`, `npm.cmd run typecheck` 통과 |
| [P3 감시 상태 표현 중복](https://github.com/team-youngkk/masit-on/pull/460#discussion_r4581326183) | 집계 배지의 라벨·tone을 `watchStatusPresentation`과 통일 | 애플리케이션 | 수정 필요 | 집계도 공통 상태 presentation을 순회해 라벨·tone·순서를 결정하도록 변경했다. | `YoutubeChannelWatchScreen.tsx` 확인, typecheck 통과 |
| [P3 confidence 임계값 중복](https://github.com/team-youngkk/masit-on/pull/460#discussion_r4581326186) | 화면 라벨과 배지 tone이 같은 confidence 정책을 사용 | 애플리케이션 | 수정 필요 | `aiConfidencePresentation`으로 라벨·퍼센트·임계값을 공통화했다. | confidence 경계 회귀 테스트 통과 |
| [P3 AI 상태 라벨 중복](https://github.com/team-youngkk/masit-on/pull/460#discussion_r4581326189) | 목록·상세·등록 단위의 상태 매핑을 공통화 | 애플리케이션 | 수정 필요 | 실행·완전성·검수·유입 경로·등록 단위 라벨과 tone을 coordination 모듈로 옮겼다. | 공통 표현 회귀 테스트 통과 |
| [P3 보충 검증 접두사 제거](https://github.com/team-youngkk/masit-on/pull/460#discussion_r4581326194) | 문자열 접두사를 정규식으로 다시 제거하지 말고 라벨을 직접 사용 | 애플리케이션 | 수정 필요 | `AI_BLOCK_REASON_LABELS`를 직접 렌더링해 문구 결합 의존성을 제거했다. | 전체 프론트 테스트 통과 |
| [P2 비정상 confidence tone](https://github.com/team-youngkk/masit-on/pull/460#discussion_r4582770574) | “확인 필요” 라벨과 성공 tone의 불일치 제거 | 애플리케이션 | 수정 필요 | label·percentage·tone을 하나의 presentation 결과로 반환하고 상세 화면이 같은 결과를 사용하도록 변경했다. | `NaN`·`null` tone 경계 테스트 통과 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: 없음. 화면의 운영 의미가 코드 중복과 페이지 범위 은닉으로 어긋날 수 있었다.
- 발생 환경: PR #460 관리자 운영 화면, AI 영상 추출 목록·상세·등록 단위와 YouTube 채널 감시 화면.
- 재현 조건: 50개 단위 페이지 응답에서 감시 상태 집계를 확인하거나, 목록·상세·등록 단위에서 같은 AI 상태를 동시에 확인하거나, 정상 범위를 벗어난 confidence 값 또는 confidence 정책 임계값을 사용하는 경우.
- 실제 결과: 현재 페이지 집계가 전체 채널 집계처럼 보일 수 있었고, 같은 상태가 화면 위치에 따라 다른 라벨로 보일 수 있었다. confidence 라벨과 tone, 보충 검증 오류 표현도 각각의 구현에 의존했다.
- 기대 결과: 운영자가 집계 범위와 상태 의미를 오해하지 않고, 하나의 표현 정책이 관련 화면 전체에 적용되어야 한다.
- 영향 범위: 관리자 판단·재시도·감시 상태 확인의 표시 일관성. API 요청·응답, 권한, 상태 전이는 영향이 없다.

## 4. 근본 원인

PR #460에서 운영 화면의 표시를 먼저 정리하면서 일부 presentation 정책이 각 컴포넌트 안에 남았다. 채널 집계는 목록 응답의 현재 페이지를 사용하면서 접근성 라벨에만 범위를 적었고, 화면에 보이는 안내에는 범위를 표시하지 않았다. AI 목록·상세·등록 단위는 동일한 상태 매핑을 각각 복제했으며, confidence 라벨은 `ai-confidence.ts`의 tone 임계값을 다시 하드코딩했다. 후속 검토에서 비정상 confidence의 라벨과 tone도 서로 다른 경로에서 계산되는 문제가 확인됐다. 보충 검증 문구는 이미 라벨을 포함한 함수 결과를 정규식으로 되돌려 표시했다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| GitHub PR #460의 미해결 인라인 스레드와 최신 head diff 확인 | P2 2건, P3 4건 모두 현재 코드에서 재현 가능한 표시 정합성 문제 | 6건 모두 수정 필요로 분류 |
| 기존 `pr-227`·`pr-170` 트러블슈팅 기록과 관리자 API·ADR 확인 | 감시 상태·AI 추출·권한 계약은 유지되어야 함을 확인 | 화면 표시와 coordination 함수만 변경 |
| 목록·상세·등록 단위의 상태 함수 대조 | 실행·완전성·검수·유입 경로 매핑이 중복됨 | 공통 coordination 모듈로 이동 |
| confidence tone과 화면 라벨 임계값 대조 | tone은 0.6·0.8 상수를 사용하지만 라벨은 같은 숫자를 별도 보유 | presentation 함수로 임계값 단일화 |
| 보충 검증 실패 렌더링 경로 확인 | `aiValidationFailureMessageFor` 결과의 접두사를 정규식으로 제거 | `AI_BLOCK_REASON_LABELS` 직접 사용 |
| isolated worktree의 의존성 상태 확인 | 기본 worktree와 달리 `node_modules`가 없음 | 기존 의존성을 임시 Junction으로 연결해 테스트·typecheck를 실행하고 검증 후 제거 |

## 6. 최종 해결

- `YoutubeChannelWatchScreen`은 `watchStatusPresentation`을 사용해 집계 라벨과 tone을 결정하고, 집계가 현재 페이지 기준임을 화면에 표시한다.
- `ai-video-extractions-coordination.ts`에 실행·완전성·검수·유입 경로·등록 단위 상태 presentation을 추가하고 목록·상세·등록 단위가 이를 공유한다.
- `ai-confidence.ts`에 `aiConfidencePresentation`을 추가해 tone과 라벨의 0.6·0.8 임계값을 한 곳에서 관리한다.
- 비정상 confidence는 presentation에서 `확인 필요`와 `danger` tone을 함께 반환하고, 상세 화면은 해당 결과와 안전한 값 표시를 공유한다.
- `AiRegistrationUnits`는 보충 검증 사유를 `AI_BLOCK_REASON_LABELS`에서 직접 읽고 문자열 접두사 제거를 없앴다.
- 공통 상태 표현과 confidence 경계에 회귀 테스트를 추가했다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `npm.cmd test` | 통과 | 387 tests, 387 passed, 0 failed |
| `npm.cmd run typecheck` | 통과 | TypeScript 검사 통과 |
| `git diff --check` | 통과 | 공백 오류 없음 |
| `node .../avoid-ai-design/scripts/detect.mjs` 변경 6개 파일 | 통과 | P0/P1/P2 0건 |
| `npm.cmd run build` | 미완료 | 테스트 387건과 TypeScript 검사는 통과했으나 임시 Junction `node_modules`를 Turbopack이 프로젝트 루트 밖 symlink로 거부했다. |

## 8. 재발 방지 및 다음 확인

- AI 상태 표현·confidence 경계·등록 단위 상태 라벨의 공통 회귀 테스트를 유지한다.
- 채널 감시 집계가 서버 전체 집계로 바뀌는 경우 API 계약과 화면 문구를 함께 갱신해야 한다.
- PR head push 후 GitHub Actions의 프론트 build 결과를 확인한다. 로컬 Turbopack 실패는 임시 의존성 Junction 경계에서 발생했다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| 관리자 화면 상태 표현 중복 | 목록·상세·등록 단위에 동일 매핑 함수가 각각 존재 | 정적 코드 검색과 공통 함수 테스트 | 미측정 | 공통 coordination 함수로 구조상 단일화 | PR #460 병합 전 코드 리뷰 |
| 감시 집계 범위 오해 가능성 | 화면 문구에 현재 페이지 범위 없음 | 50개 초과 채널의 2페이지 화면 확인 | 미측정 | 현재 페이지 기준을 화면에 명시 | WS-04 담당자, 병합 후 수동 확인 |

## 10. 남은 사항

- 코드 수정·테스트·typecheck·focused scanner는 완료했다.
- 로컬 build는 임시 dependency Junction을 Turbopack이 거부해 미완료이며, Junction은 검증 후 제거했다. PR head push 후 GitHub Actions 결과를 확인해야 한다.
- 리뷰 스레드는 원격 수정 커밋과 검증 결과를 게시한 뒤 각각 답글과 resolve를 처리한다.
