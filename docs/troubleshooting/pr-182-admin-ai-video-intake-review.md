---
related_documents:
  - ../../.codex/skills/troubleshoot-pr-review/SKILL.md
  - ../../docs/00-overview/scope.md
  - ../../docs/04-product/wireframes/third-expansion-wireframes.md
  - ../../docs/05-specs/api/admin/ai-video-extraction-api.md
  - ../../docs/06-architecture/technology-policy.md
  - ../../frontend/components/admin/AiVideoExtractionList.tsx
  - ../../frontend/lib/admin/ai-video-extractions-coordination.ts
  - ../../frontend/lib/member/collections-coordination.ts
  - ../../frontend/lib/admin/api.ts
  - ../../src/test/java/com/masiton/security/infrastructure/redis/RedisRefreshTokenStoreIntegrationTest.java
---

# PR #182 리뷰 트러블슈팅: 관리자 AI 영상 접수 보완

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#182](https://github.com/team-youngkk/masit-on/pull/182) |
| 작성자 | 김인안 |
| 처리 일자 | 2026-08-13 |
| 범위 | 관리자 AI 영상 신규 접수 화면 후속 리뷰 반영, 테스트 스크립트 정리와 Redis 통합 테스트 안정화 |
| 주 문제 유형 | 프론트엔드 상태 / 접근성 / 개인정보 / 테스트·CI / Git 협업 |
| 기존 기록 | [PR #170 AI 영상 추출 Provider·Webhook 리뷰와 CI 실패 반영](pr-170-ai-video-extraction-review.md) 확인 |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| `AiVideoExtractionList.tsx:71` | 접수 중 다음 입력이 이전 요청의 성공 응답으로 지워질 수 있음 | 프론트엔드 상태 | 수정 필요 | 제출 중 URL·보완 텍스트를 함께 비활성화 | `npm.cmd test`, `npm.cmd run typecheck` |
| `AiVideoExtractionList.tsx:86` | Google Gemini 전송 범위·임시 보존·삭제 정책을 제출 전에 명시해야 함 | 개인정보 | 수정 필요 | URL·보완 텍스트 전송 범위, 암호화 임시 보존과 24시간 이내 삭제, 미보존 대상을 안내 | 화면 문구와 `docs/00-overview/scope.md` 대조 |
| `AiVideoExtractionList.tsx:67` | `reused=true` 응답에 기존 작업 ID·상태·기존 작업 링크가 필요함 | 프론트엔드 계약 | 수정 필요 | 응답 객체를 보존하고 기존 ID·실행 상태·링크 라벨을 표시 | 신규 coordination 테스트 |
| `AiVideoExtractionList.tsx:94` | 입력 오류를 필드 바로 아래에 연결하고 보조기술에 노출해야 함 | 접근성 | 수정 필요 | 필드별 오류, `aria-invalid`, `aria-describedby`, `role=alert` 추가 | 신규 field-error 테스트 및 typecheck |
| `AiVideoExtractionList.tsx:74` | 필터 변경 중 접수 완료 재조회가 이전 필터를 사용할 수 있음 | 프론트엔드 경합 | 수정 필요 | 접수 완료·수동 새로고침을 현재 필터를 읽는 effect 재실행으로 전환 | requestId 보호 코드와 typecheck 확인 |
| `AiVideoExtractionList.tsx:104` | `role=alert`에 `aria-live=polite`가 함께 지정됨 | 접근성 | 수정 필요 | 오류 상태에서는 `aria-live`를 제거해 alert의 assertive 동작을 보존 | `npm.cmd run typecheck` |
| `AiVideoExtractionList.tsx:50` | 상태 반영 전 빠른 중복 submit이 동시에 POST할 수 있음 | 프론트엔드 상태 | 수정 필요 | ref 기반 동기 in-flight guard와 기존 멱등키를 함께 적용 | `npm.cmd test` |
| `AiVideoExtractionList.tsx:68` | 멱등키 생성 예외 시 `submitInFlight`가 영구 잠길 수 있음 | 프론트엔드 상태 | 수정 필요 | fingerprint·멱등키 생성까지 `try/finally` 범위에 포함해 예외 뒤에도 잠금 해제 | 프론트 테스트·typecheck |
| `AiVideoExtractionList.tsx:89` | 서버 URL 오류에서 URL 입력으로 포커스가 이동하지 않음 | 접근성 | 수정 필요 | 오류 메시지 설정과 함께 URL 입력에 포커스 | 프론트 typecheck |
| `ai-video-extractions-coordination.ts:29` | 프로덕션에서 사용하지 않는 검증 함수가 남아 있음 | 유지보수 | 수정 필요 | 함수와 전용 테스트를 제거하고 필드 오류 함수를 단일 경로로 유지 | 프론트 테스트 |
| `ai-video-extractions-coordination.ts:13` 외 | 멱등키 생성 로직이 도메인별로 중복됨 | 구조 / 유지보수 | 수정 필요 | `frontend/lib/idempotency.ts`로 공용화하고 관리자·컬렉션 흐름에서 재사용 | `npm.cmd test`, `npm.cmd run typecheck` |
| `ai-video-extractions.ts:129` | 관리 화면과 접수 화면 오류 매핑 함수가 중복됨 | 구조 / 유지보수 | 수정 필요 | context 인자를 받는 `aiExtractionMessageFor` 하나로 통합 | AI 접수 API 테스트 |
| `AiVideoExtractionScreen.module.css:5` | 접수 폼 입력 스타일이 재시도 폼과 중복됨 | CSS / 유지보수 | 수정 필요 | 공통 선택자 그룹으로 통합 | `npm.cmd run build` |
| `ai-video-extractions-coordination.ts:22` | UTF-16 길이 검증이 다른 프론트 검증기와 다름 | 계약 검토 | 수정 불필요 | 백엔드 Java `String.length()`와 20,000자 계약이 일치해 이번 범위에서 유지 | 백엔드 계약·기존 경계 테스트 대조 |
| `AiVideoExtractionList.tsx:74` | 새 작업과 무관한 필터에서도 성공 후 목록을 재조회함 | 효율성 | 수정 불필요 | 현재 필터를 기준으로 최신 목록을 보장하는 의도된 동작이며 기능 정확성 변경과 분리 | 새 필터 effect 경로 확인 |
| CI `31590500905` | 백엔드 전체 테스트에서 레거시 세션 복구 큐 테스트가 실패함 | CI / 테스트 격리 | 수정 필요 | 테스트 스케줄러를 격리하고 Redis 실제 시각에 맞는 만료 테스트 시각을 사용 | 로컬에서 동일 실패 재현 후 수정 |
| [백엔드 테스트 범위](https://github.com/team-youngkk/masit-on/pull/182#discussion_r3771213551) | Redis 테스트 격리 변경이 프론트 PR 범위와 분리되지 않고 본문에도 누락됨 | Git | 수정 필요 | 이번 PR에 CI 차단 원인과 백엔드 테스트 변경을 명시하고, 별도 PR로 나누지 않은 이유를 본문에 반영 | PR 본문 갱신 필요; 로컬 변경은 `RedisRefreshTokenStoreIntegrationTest`에 한정 |
| [Redis 시각 픽스처](https://github.com/team-youngkk/masit-on/pull/182#discussion_r3771213558) | `Instant.now().minusSeconds(5)`와 `plusSeconds(10)`의 근거가 없고 자매 테스트와 기준이 다름 | 애플리케이션 | 수정 필요 | `REVOKE_ALL_SCRIPT`의 Redis 실제 시각 사용을 주석으로 설명하고 `Instant.now()` 기준으로 단순화 | Docker 미사용 환경에서는 Testcontainers 초기화 단계에서 실행 불가 |
| [Node 실험 플래그](https://github.com/team-youngkk/masit-on/pull/182#discussion_r3771213562) | 필수 CI 경로가 `--experimental-transform-types`에 의존함 | 애플리케이션 | 수정 필요 | `AdminApiError` parameter property를 명시적 필드 대입으로 바꾸고 API 테스트를 기존 Node 테스트 프로세스에 통합 | `npm.cmd test`, `npm.cmd run typecheck` |
| [테스트 수치](https://github.com/team-youngkk/masit-on/pull/182#discussion_r3771213567) | 기록의 테스트 수치가 실행 결과와 불일치함 | 기타 | 수정 필요 | coordination·API 테스트를 합산한 실제 결과 184개로 통일 | `npm.cmd test` 결과 `184 pass` |
| [컬렉션 멱등키](https://github.com/team-youngkk/masit-on/pull/182#discussion_r3771213570) | 공용 helper를 호출하기 전에 동일 fingerprint 조기 반환으로 규칙이 중복됨 | 애플리케이션 | 수정 필요 | helper 결과를 단일 판정 경로로 사용하되 기존 객체 재사용 동작은 보존 | `npm.cmd test`의 컬렉션 재시도 시나리오 |
| [빈 URL 검증](https://github.com/team-youngkk/masit-on/pull/182#discussion_r3771213573) | `required`의 브라우저 검증이 필드별 커스텀 오류·ARIA 경로를 우회함 | 애플리케이션 | 수정 필요 | `noValidate`로 커스텀 필드 검증 경로를 단일화하고 빈 문자열·공백 회귀 검증 추가 | `npm.cmd test`, `npm.cmd run typecheck` |
| [보완 텍스트 오류 문구](https://github.com/team-youngkk/masit-on/pull/182#discussion_r3771213576) | `trim()` 검증을 “공백 제외”로 안내해 중간 공백까지 제거하는 것으로 오해할 수 있음 | 애플리케이션 | 수정 필요 | “앞뒤 공백을 제외하고”로 API 계약과 문구를 일치 | coordination 테스트 통과 |

## 3. 문제 현상과 발생 조건

- 제출 요청이 진행되는 동안 제출 버튼만 잠겨 URL과 보완 텍스트를 수정할 수 있었고, 이전 요청이 성공하면 입력 상태를 무조건 빈 문자열로 만들었다.
- 재사용 응답은 메시지와 작업 상세 링크만 표시해 기존 작업 ID와 당시 실행 상태를 바로 확인할 수 없었다.
- URL과 보완 텍스트 검증 오류를 한 문장으로 합쳐 form-level 안내만 표시해 어느 필드를 수정해야 하는지, 보조기술이 오류와 입력을 어떻게 연결해야 하는지 알 수 없었다.
- 접수 성공 시 렌더링에 캡처된 `load`를 직접 호출해, 요청 중 실행 상태가 바뀐 필터와 다른 필터로 목록이 갱신될 수 있었다.
- 제출 전 안내가 보완 텍스트를 결과 화면에 다시 표시하지 않는다는 내용에 그쳐 Google Gemini 전송 범위와 임시 보존 정책을 알리지 않았다.
- 오류 안내의 `role=alert`와 `aria-live=polite`가 충돌했고, 상태 갱신 전 빠른 중복 submit을 막는 동기 ref guard가 없었다.
- 멱등키 생성과 오류 메시지 매핑, 입력 CSS에 동일한 로직·선언이 반복되어 변경 지점이 분산되어 있었다.
- `creationAttemptFor`가 공용 멱등키 helper보다 먼저 동일 이름을 판정해 공용 규칙이 모든 경로의 단일 기준이 되지 못했다.
- 빈 URL은 HTML constraint validation이 커스텀 필드 오류보다 먼저 동작했고, 보완 텍스트 오류 문구는 `trim()`의 실제 범위를 과장했다.
- `AdminApiError`의 parameter property 때문에 프론트 필수 테스트가 Node 실험 플래그와 별도 프로세스를 필요로 했으며, PR 본문과 기록의 테스트 수치도 실행 프로세스 기준으로 분리되어 있었다.
- PR의 백엔드 테스트 격리 변경은 프론트 기능과 같은 CI 차단 원인을 해결했지만 PR 본문에 범위와 검증 명령이 드러나지 않았다.

## 4. 근본 원인

컴포넌트가 비동기 요청의 입력·필터 스냅샷과 현재 화면 상태를 분리해 관리하지 않았다. 입력은 요청 완료 시 무조건 초기화했고, 목록 재조회는 submit 함수가 생성된 시점의 `load`를 사용했다. 또한 API 결과 객체 중 `jobId`만 상태로 보존했으며, 검증 결과는 문자열 배열로만 반환해 필드 연결 정보를 잃었다. 개인정보 안내도 PRD·와이어프레임의 전송·보존 계약을 화면에 모두 반영하지 못했다.

추가로 오류 알림의 암묵적 assertive semantics를 명시적 polite live region이 덮었고, React state의 다음 렌더 반영을 기다리는 동안 중복 submit을 즉시 차단하지 않았다. 멱등키·오류 문구·입력 스타일을 각 도메인에 복사해 두어 동일 규칙의 수정 비용도 커지고 있었다. 제출 fingerprint와 멱등키 생성이 guard 설정 이후 `try` 바깥에 있어 생성기 예외가 발생하면 `finally`에 도달하지 못했고, URL 오류 경로도 필드 오류만 설정해 포커스를 이동하지 않았다. Redis 통합 테스트는 전역 스케줄러가 테스트 큐를 경쟁적으로 소비할 수 있었고, `revokeAll`이 Redis 실제 시각으로 TTL을 판정하는데 테스트가 고정된 과거 만료 시각을 사용해 실행일에 따라 정상 큐 항목이 만료될 수 있었다.

후속 리뷰에서 확인된 근본 원인은 공용 helper 도입이 반환 객체 형태와 기존 reference identity까지 고려하지 않은 채 적용된 점, 브라우저 기본 constraint validation과 애플리케이션 오류 계약을 동시에 활성화한 점, Node의 strip-only TypeScript 실행 제약을 기술 정책과 테스트 스크립트에서 분리해 설명하지 않은 점이다. Redis 전체 폐기 경로는 애플리케이션 `Clock`이 아니라 Redis Lua의 `TIME`을 사용하므로, 이 경로만 실제 실행 시점에 맞춘 fixture가 필요하다. 이는 ISSUE·ROTATE 경로의 고정 시각 자매 테스트와 다른 원인이다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| 관리자 AI 접수 화면과 coordination 모듈 대조 | 입력 초기화와 캡처된 `load` 호출을 확인 | 입력 잠금과 refresh version effect로 최소 수정 |
| `docs/00-overview/scope.md` 및 3차 확장 와이어프레임 대조 | Gemini 영상 입력, 보완 텍스트의 암호화 임시 저장·24시간 이내 삭제, 원본·전체 자막·전체 응답 미보존 계약 확인 | 제출 전 안내 문구에 계약을 요약해 반영 |
| `npm.cmd test` | 184개 테스트 통과 | coordination·AI 접수 API 테스트를 하나의 Node 프로세스에서 실행하고 field error·재사용·멱등키 회귀 검증 |
| `npm.cmd run typecheck` | 통과 | 컴포넌트·API·coordination 타입 연결 확인 |
| CI run `31590500905` job·로그 확인 | 프론트엔드 빌드·타입 검사는 통과. 백엔드는 `RedisRefreshTokenStoreIntegrationTest.memberSession_레거시만료시각_전체폐기_복구큐선적재`에서 실제 결과 `[]`와 기대 세션 ID가 달라 실패 | 로컬에서 동일 실패를 재현. 테스트 컨텍스트에서 스케줄러 bean을 mock하고, Redis 실제 시각보다 충분히 미래인 실행 시점 기반 TTL을 사용 |
| `node --test` 플래그 제거 전후 실행 | `AdminApiError` parameter property 때문에 실험 플래그가 필요했으나 명시적 필드 대입 후 일반 strip-only 실행이 통과 | 테스트 파일을 별도 프로세스로 유지할 이유가 없어 기존 테스트 명령에 통합 |
| Redis 통합 테스트 로컬 실행 | 테스트 코드가 실행되기 전에 Testcontainers가 Docker 환경을 찾지 못해 초기화 실패 | 코드 변경 검증은 완료하지 못했으며 Docker가 있는 CI 재실행이 필요 |
| PR 본문·현재 diff 대조 | 백엔드 테스트 변경과 Gradle 검증 명령이 PR 본문에는 누락 | 본문에 변경 범위·분리하지 않은 이유·Gradle 결과를 추가해야 함 |

## 6. 최종 해결

- `AiVideoExtractionList`는 접수 중 URL과 보완 텍스트를 함께 비활성화해 진행 중 요청이 완료될 때 새 입력이 사라지는 경로를 차단한다.
- 접수 성공과 수동 새로고침은 `refreshVersion`을 증가시키고, effect가 렌더링 시점의 현재 필터로 `load`를 실행한다. 기존 `requestId` 보호와 결합해 오래된 목록 응답을 반영하지 않는다.
- `AiExtractionSubmissionResult` 전체를 보존해 재사용 응답이면 기존 작업 ID, 현재 실행 상태, `기존 작업 보기` 링크를 표시한다.
- 입력 검증 결과를 `videoUrl`·`supplementText` 필드 오류로 구조화하고 `aria-invalid`·`aria-describedby`와 필드 하단 오류를 렌더링한다. 서버의 공개 YouTube URL 오류도 URL 필드에 연결한다.
- 제출 전 안내에 Google Gemini 전송 입력 범위, 보완 텍스트의 암호화 임시 보존 및 작업 종료 후 24시간 이내 삭제, 원본 영상·전체 자막·Provider 응답 전문 미보존을 명시한다.
- `role=alert` 오류에는 `aria-live`를 덧붙이지 않고, 정상 상태에만 `aria-live=polite`를 사용한다. 제출 시작 전 ref guard를 세워 빠른 중복 POST를 차단한다.
- 제출 fingerprint·멱등키 생성과 API 호출을 모두 `try/catch/finally` 안에 두어 생성 예외에도 in-flight guard가 해제되도록 하고, 서버 URL 오류는 오류 표시와 함께 URL 입력으로 포커스를 이동한다.
- Redis 저장소 통합 테스트에서는 복구 큐를 소비하는 전역 스케줄러 bean을 mock하고, Redis 실제 시각과 어긋나는 고정 과거 TTL 픽스처를 사용하지 않는다.
- 멱등키 생성은 `frontend/lib/idempotency.ts`로 공용화하고, 오류 메시지는 context 인자로 관리·접수 문구를 분기하며, 접수·재시도 폼의 공통 CSS를 묶었다.
- 컬렉션 멱등키는 공용 helper가 동일 fingerprint를 판정하도록 하고, 반환 객체 변환 뒤에도 기존 재시도 객체 재사용 동작을 회귀 테스트로 보존한다.
- HTML constraint validation을 사용하지 않는 커스텀 오류 계약에서는 `noValidate`와 빈 문자열·공백 입력 테스트를 함께 유지한다.
- Node 실행 스크립트는 실험 플래그에 의존하지 않도록 parameter property를 피하고, 관련 API 테스트를 동일한 고정 런타임 테스트 명령에 포함한다.
- PR 본문은 실제 변경 파일에 맞춰 프론트·백엔드 범위와 각 검증 명령을 함께 기록한다. 이번 PR의 백엔드 변경은 CI 차단 원인과 테스트 격리 수정이므로 별도 PR로 분리하지 않은 이유를 본문에 남긴다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `npm.cmd test` | 통과 | 자연어 사전 테스트 10개와 본 테스트 184개 통과 |
| `npm.cmd run typecheck` | 통과 | Next.js TypeScript 컴파일 오류 없음 |
| `npm.cmd run build` | 통과 | 테스트 184개·타입 검사·Next.js production build 성공; 로컬 Node 24.14.0으로 `engines` 경고가 있어 고정 CI 런타임 재확인 필요 |
| `gradlew.bat test --tests RedisRefreshTokenStoreIntegrationTest` | 실행 불가 | Testcontainers가 로컬 Docker 환경을 찾지 못해 초기화 단계에서 실패; 테스트 코드 실패와 구분 |
| `git diff --check` | 통과 | 변경 파일 공백 오류 없음 |
| 기존 head의 GitHub Actions run `31599577086` | 통과 | 현재 로컬 후속 변경 전 head 기준 결과이며, 새 변경 push 후 CI 재확인 필요 |

## 8. 재발 방지 및 다음 확인

- 비동기 form은 요청 시작 시 입력 잠금 또는 제출 fingerprint guard 중 하나를 명시하고, 결과 반영 시 현재 화면 상태를 덮어쓰지 않는 회귀 시나리오를 유지한다.
- 비동기 form은 state 반영 전에도 ref 기반 in-flight guard로 중복 요청을 차단하고, `alert`의 암묵적 assertive semantics를 polite live region으로 덮지 않는다.
- 멱등키·오류 매핑·공통 입력 스타일은 재사용 가능한 모듈을 우선 사용해 도메인별 복사 구현을 만들지 않는다.
- 목록 재조회는 캡처된 필터를 직접 호출하지 않고 현재 필터 effect와 request generation을 통해 실행한다.
- 외부 AI 입력 화면은 요청 전에 전송 범위·보존 기간·삭제 시점·미보존 데이터를 함께 표시하고, 계약 문구가 바뀌면 PRD·와이어프레임과 화면을 대조한다.
- 새 head push 뒤 프론트·백엔드 CI를 다시 확인한다. 백엔드 실패가 계속되면 실패 테스트와 인프라 연결 오류를 분리해 후속 PR로 처리한다.
- 로컬 Docker가 없는 경우 Redis Testcontainers 결과를 코드 수정 실패로 해석하지 않고, CI 또는 Docker가 활성화된 환경에서 동일 명령을 재실행한다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 도입 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| 접수 중 입력 유실 경로 | 1개: 성공 응답의 무조건 초기화 | 코드 경로 검토 | 0개: 두 입력 비활성화 | 리뷰 재현 조건 제거 | PR #182 / 2026-08-12 |
| 재사용 작업 상태 식별성 | ID·상태 미표시 | 응답 렌더링 검토 | 기존 ID·실행 상태·링크 표시 | 중복 접수 결과 식별 가능 | PR #182 / 2026-08-12 |
| 필드 오류 연결 | form-level 문자열 1개 | 오류 렌더링·ARIA 검토 | 필드별 오류 2종 | 수정 대상과 보조기술 연결 가능 | PR #182 / 2026-08-12 |
| Node 실험 플래그 의존 | 1개 필수 CI 스크립트 | `frontend/package.json`과 일반 `node --test` 실행 비교 | 0개 | 고정 런타임에서 실험 플래그 없이 테스트 가능 | PR #182 / 2026-08-13 |
| 테스트 프로세스 분리 | 2개 Node test 명령 | `npm.cmd test` 스크립트 검토·실행 | 1개 | API 테스트를 일반 테스트 게이트에 통합 | PR #182 / 2026-08-13 |
| Redis 통합 테스트 통과율 | 측정 불가: 로컬 Docker 미사용 | Testcontainers 초기화 단계에서 중단 | 측정 대기 | Docker가 있는 CI에서 동일 명령 확인 필요 | PR #182 / CI 재실행 |

## 10. 남은 사항

- 로컬 프론트 테스트·typecheck·production build는 통과했다. Redis 통합 테스트는 로컬 Docker 부재로 실행하지 못했다. 현재 변경을 원격에 반영한 뒤 PR 본문 갱신, 스레드 답글·resolve와 GitHub Actions 재실행이 남아 있다.
