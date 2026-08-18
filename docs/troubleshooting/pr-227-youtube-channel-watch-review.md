---
related_documents:
  - ../01-requirements/functional-requirements.md
  - ../02-analysis/third-expansion-workstreams.md
  - ../03-team/roles.md
  - ../05-specs/api/admin/ai-video-extraction-api.md
  - ../05-specs/api-traceability.md
  - ../06-architecture/transaction-boundaries.md
  - ../05-specs/data/migration-plan.md
  - ../../src/main/java/com/masiton/ai/application/YoutubeChannelWatchManagementService.java
  - ../../src/main/java/com/masiton/ai/infrastructure/persistence/JdbcYoutubeChannelWatchStore.java
  - ../../src/test/java/com/masiton/ai/infrastructure/persistence/JdbcYoutubeChannelWatchStoreIntegrationTest.java
  - ../../frontend/lib/admin/youtube-channel-watches-coordination.ts
  - ../../src/test/java/com/masiton/security/SecurityBoundaryApiTest.java
---

# PR #227 리뷰 트러블슈팅: YouTube 채널 감시 상태·관리 화면 후속 반영

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [PR #227](https://github.com/team-youngkk/masit-on/pull/227) |
| 작성자 | @inan0226 |
| 처리 일자 | 2026-08-18 |
| 범위 | 채널 감시 재시작 UX, Creator 상태 조회, 다중 유튜버 동시 감시, 보안 증거, 오류 시각 계약, CI 마이그레이션 검증, 프런트 공용화와 협력자 경계, 리뷰 후속 페이징·접근 불가 감시 중지 |
| 주 문제 유형 | 애플리케이션 / 데이터베이스 / 기타(계약·문서) |
| 기존 기록 | [PR #184 YouTube 채널 감시 상태·동시성 경계](pr-184-youtube-channel-watch-review.md)를 확인했다. 기존 기록은 challenge·동시성·외부 구독 실패 경계에 집중되어 있어 이번 상태 조회·화면·오류 시각 문제와 직접 겹치지 않는다. 오류 시각은 요구사항에 맞춰 신규 마이그레이션으로 보완했다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [RENEWAL_FAILED 재시작](https://github.com/team-youngkk/masit-on/pull/227#discussion_r3800281592) | 실패 상태에서 `enabled=true` 재시도와 상태별 토글 문구 제공 | 애플리케이션 | 수정 필요 | `watchToggleEnabled`가 실패 상태를 재시작으로 분리하고 회귀 테스트를 추가 | 프런트 테스트 통과 |
| [채널 감시 보안 증거](https://github.com/team-youngkk/masit-on/pull/227#discussion_r3800281600) | 정확한 GET·PUT 경로의 401·403·ADMIN 테스트 연결 | 애플리케이션 | 수정 필요 | `SecurityBoundaryApiTest`에 두 경로의 미인증·비관리자·ADMIN 시나리오 추가, 추적표에 연결 | 컴파일 통과. full-context 실행은 Docker 데몬 부재로 차단 |
| [마지막 오류 시각](https://github.com/team-youngkk/masit-on/pull/227#discussion_r3800285522) | `lastErrorAt` 추가 또는 미제공 사유 명시 | 데이터베이스 / 기타 | 수정 필요 | V5 마이그레이션, Store·UseCase·Controller·프런트 응답과 오류 기록·성공 초기화, 계약 문서 반영 | 컴파일·Controller 테스트 통과, PostgreSQL 통합 테스트는 Docker 필요 |
| [사이드바 활성 항목](https://github.com/team-youngkk/masit-on/pull/227#discussion_r3800285524) | 하위 메뉴가 부모와 동시에 활성화되지 않도록 조정 | 애플리케이션 | 수정 필요 | 일치하는 경로 중 가장 긴 `href`만 활성화 | 프런트 타입 검사·빌드 통과 |
| [화면 상세 중복](https://github.com/team-youngkk/masit-on/pull/227#discussion_r3800285525) | 헤더·오류 안내와 중복되는 상세 행 제거 | 애플리케이션 | 수정 필요 | `활성화 요청`, `최근 구독 처리 안내` 행 제거, 오류 시각 행 추가 | 프런트 테스트·빌드 통과 |
| [GET Creator 검증 조건](https://github.com/team-youngkk/masit-on/pull/227#discussion_r3800323104) | PUT 비활성화 후에도 GET으로 상태를 확인할 수 있도록 완화 | 애플리케이션 | 수정 필요 | GET은 채널 식별자가 있는 Creator를 조회하고, `enabled=true`만 공개·외부 이용 가능을 검증 | 서비스 회귀 테스트 통과 |
| [stale Creator 선택](https://github.com/team-youngkk/masit-on/pull/227#discussion_r3800323113) | 목록에서 사라진 선택값을 첫 유효 Creator로 복구 | 애플리케이션 | 수정 필요 | 선택값이 목록에 없으면 첫 Creator를 재선택하는 effect로 변경 | 프런트 타입 검사·빌드 통과 |
| [Creator 목록 공용화](https://github.com/team-youngkk/masit-on/pull/227#discussion_r3800323117) | `/api/creators` 호출·응답 매핑을 공용화 | 애플리케이션 | 수정 필요 | `frontend/lib/creators-api.ts`에 `fetchCreatorReferences`를 추가하고 등록·감시 화면이 공유 | 프런트 테스트·빌드 통과 |
| [AI 오류 메시지 패턴](https://github.com/team-youngkk/masit-on/pull/227#discussion_r3800323125) | 오류 코드 매핑을 기존 `...MessageForCode ?? messageFor` 패턴과 통일 | 애플리케이션 | 수정 필요 | `youtubeChannelWatchMessageForCode`를 분리하고 동일한 fallback 구조 적용 | 프런트 타입 검사·빌드 통과 |
| [오류 맵 중복](https://github.com/team-youngkk/masit-on/pull/227#discussion_r3800323130) | 메시지·라벨 Record를 하나의 타입 맵으로 통합 | 애플리케이션 | 수정 필요 | `ERROR_PRESENTATIONS` 단일 `Record<string, {message; label}>`로 통합 | 프런트 테스트 통과 |
| [오류 안내 중복](https://github.com/team-youngkk/masit-on/pull/227#discussion_r3800323134) | 오류 메시지와 의미 없는 `기록 없음` 상세 행 중복 제거 | 애플리케이션 | 수정 필요 | 중복 행 제거 및 실제 `lastErrorAt` 표시로 대체 | 프런트 빌드 통과 |
| [Persistence 협력자 경계](https://github.com/team-youngkk/masit-on/pull/227#discussion_r3800323138) | 서비스가 Store를 직접 의존하지 않고 기존 Persistence 위임 사용 | 애플리케이션 | 수정 필요 | `YoutubeChannelWatchPersistenceService.findDetail` 추가 후 서비스가 위임 경로 사용 | 백엔드 컴파일·서비스 테스트 통과 |
| 다중 채널 동시 감시 | 한 화면에서 여러 유튜버 상태를 조회하고 각 행을 독립적으로 토글 | 애플리케이션 | 수정 필요 | collection GET, 배치 상태 조회, 행별 mutation·오류 격리 추가 | 백엔드 컴파일·프런트 테스트/typecheck 통과 |
| CI V4/V5 기대 분리 | V4 대상 테스트가 V5 `last_error_at`까지 요구 | 데이터베이스 / 기타 | 수정 필요 | 마이그레이션 기대 컬럼을 V4·V5 대상별로 분리하고 Prompt 문서 P7을 정합화 | Prompt 계약 테스트 통과, Docker 통합은 CI 재실행 필요 |
| [공개 목록에서 빠진 활성 Watch](https://github.com/team-youngkk/masit-on/pull/227#discussion_r3800573751) | 공개·가용 목록에서 빠진 기존 Watch 소유자도 관리자 감시 목록에서 중지 가능해야 함 | 애플리케이션 | 수정 필요 | 감시 목록을 공개 후보와 기존 Watch 소유자의 DB 합집합으로 조회하고 collection API에 포함 | 백엔드 서비스·프런트 테스트 통과, PostgreSQL 통합은 CI 확인 |
| [접근 불가 RENEWAL_FAILED 중지](https://github.com/team-youngkk/masit-on/pull/227#discussion_r3800841430) | 재시작 허용과 기존 감시 중지 허용을 분리 | 애플리케이션 | 수정 필요 | `watchToggleAction`으로 접근 불가 기존 감시는 `enabled=false` 중지만 허용하고 재시작은 차단 | 프런트 전체 테스트 244개·typecheck 통과 |
| [P2~P6 역사 이력](https://github.com/team-youngkk/masit-on/pull/227#discussion_r3800842683) | 현재 P7과 함께 기존 Prompt P1~P6 보존 계약 유지 | 기타(계약·문서) | 수정 필요 | 데이터 계약 문서에 P1~P6 역사적 이력 보존을 명시 | 계약 문서 diff·프런트/백엔드 검증 통과 |
| [DB 경계 페이징](https://github.com/team-youngkk/masit-on/pull/227#discussion_r3800842686) | 전체 Creator/Watch를 읽은 뒤 애플리케이션에서 page를 자르지 않도록 수정 | 데이터베이스 | 수정 필요 | Creator·Watch 조인 경계에서 후보 `COUNT(*)`와 `ORDER BY ... LIMIT/OFFSET`을 수행하고 페이지 행의 Watch 상태를 같은 조회에서 매핑 | 백엔드 컴파일·서비스 회귀 테스트 통과, PostgreSQL 통합은 CI 확인 |
| [V4/V5 컬럼 기대 분리](https://github.com/team-youngkk/masit-on/pull/227#discussion_r3800573744) | V4 전용 시나리오에서 V5 `last_error_at`을 요구하지 않도록 분리 | 데이터베이스 | 이미 해결 | 기존 커밋에서 migration assertion helper에 적용 버전별 기대 컬럼을 분리 | CI #32094957568 백엔드 통과 |
| [현재 Prompt P7](https://github.com/team-youngkk/masit-on/pull/227#discussion_r3800573748) | V5 문서 보강 과정에서 현재 Prompt P7을 과거 값으로 되돌리지 않음 | 기타(계약·문서) | 이미 해결 | 현재 Prompt를 P7로 복원하고 P1~P6 역사 이력도 함께 명시 | CI #32094957568 백엔드 통과 |

## 3. 문제 현상과 발생 조건

- `RENEWAL_FAILED`는 백엔드에서 `enabled=true`로 남을 수 있는데 화면 토글은 `감시 중지`를 표시해 재시도 의도를 만들지 못했다.
- Creator가 감시 활성화 뒤 비공개·외부 이용 불가 상태로 바뀌면 PUT 비활성화는 가능하지만 GET이 404가 되어 결과 확인이 막혔다.
- 요구사항 [FR-AIEXTRACT-006](../01-requirements/functional-requirements.md#fr-aiextract-006-webhook-감시-채널-관리)은 마지막 오류 시각을 요구하지만 기존 테이블·응답에는 범주만 있었다.
- 기존 화면은 `/admin/ai`와 `/admin/ai/youtube-channel-watches`를 동시에 활성화했고, 상태 설명과 같은 값을 상세 목록에서 반복했다.
- 단일 선택 화면은 여러 채널을 동시에 비교·조작할 수 없고, 한 채널의 mutation 대기·실패가 다른 채널 조작까지 막을 수 있었다.
- V4까지 전진하는 Flyway 회귀 테스트의 공용 기대 목록에 V5 전용 `last_error_at`이 섞여 CI가 실패했다. 데이터 계약 문서의 현재 Prompt 표기도 실행 상수 P7과 어긋나 있었다.
- 감시 목록을 페이지 단위 API로 확장한 뒤에도 전체 Creator·Watch를 읽고 애플리케이션에서 후보 필터와 페이지 절단을 수행하고 있었다. 또한 공개 자격을 잃은 `RENEWAL_FAILED` 행은 기존 Watch를 보존해도 화면 토글이 재시작 동작만 계산해 중지 버튼을 막았다.

## 4. 근본 원인

상태 조회·변경 경계가 서로 다른 Creator 검증 조건을 사용했고, 초기 화면 설계가 `enabled`만으로 토글을 결정했다. 또한 V4 스키마에 오류 범주만 있어 오류 시각을 `updated_at`으로 대체하면 다른 갱신 시각과 의미가 섞이는 문제가 있었다. 프런트에서는 기존 Creator 참조·AI 오류 매핑·오류 표현이 감시 기능 안에 다시 작성되었다. 목록 기능이 없던 상태에서 이를 단순히 개별 GET 반복으로 구현하면 N+1 조회와 행별 실패 격리 문제가 생기므로, Creator 목록과 감시 상태를 서버에서 배치 조합하는 collection API로 확장했다. 이 과정에서 후보를 먼저 전부 적재한 뒤 페이지를 자르는 구현이 남아 있었고, `RENEWAL_FAILED`의 재시작과 중지 가능성을 하나의 boolean으로 계산한 것이 후속 문제의 원인이었다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| PR 인라인 문맥과 기존 [PR #184 기록](pr-184-youtube-channel-watch-review.md) 확인 | 기존 기록은 challenge·동시성 중심이고 이번 스레드의 상태 조회·화면 문제는 별도 | 신규 기록으로 남김 |
| `YoutubeChannelWatchManagementService`의 GET·PUT Creator 조건 비교 | GET만 공개·외부 이용 가능 조건을 무조건 요구 | GET 조건을 채널 식별자 존재 기준으로 완화하고 활성화 요청 조건은 유지 |
| `youtube_channel_watch`의 V4 컬럼과 실패 갱신 SQL 확인 | 오류 범주만 기록, `updated_at`은 일반 갱신에도 사용 | `last_error_at` 신규 컬럼과 `CURRENT_TIMESTAMP` 기록 추가 |
| 관리자 화면 토글·선택 effect·상세 목록 확인 | 실패 재시작 불가, stale 선택 복구 없음, 동일 안내 중복 | 상태별 순수 함수·선택 보정·상세 행 정리 추가 |
| `SecurityBoundaryApiTest` full-context 실행 | 로컬 Docker 데몬이 없어 `DockerClientProviderStrategy` 초기화에서 실패 | 코드 컴파일과 테스트 구성까지 검증하고 CI에서 통합 실행하도록 남김 |

## 6. 최종 해결

- `V5__add_youtube_channel_watch_last_error_at.sql`로 `last_error_at`을 추가했다.
- 구독 실패 시 오류 시각을 저장하고 challenge 성공 시 오류 범주·시각을 초기화한다. API와 프런트는 `lastErrorAt`을 함께 반환·표시한다.
- GET은 Creator 참조와 외부 채널 식별자가 있는 상태를 조회하며, 활성화 요청만 공개·외부 이용 가능 조건을 요구한다.
- `RENEWAL_FAILED`는 `감시 재시작`과 `enabled=true`로 연결한다.
- Creator 참조 조회와 오류 코드 매핑을 공용 경계로 이동하고, 오류 메시지·라벨을 단일 표현 맵으로 통합했다.
- 서비스의 상태 조회는 `YoutubeChannelWatchPersistenceService.findDetail` 위임을 사용한다.
- `GET /api/admin/ai/youtube-channel-watches`가 공개·외부 이용 가능 Creator와 기존 감시 행의 합집합을 `{items, page}`로 반환한다. 기존 감시 행은 Creator가 비공개·외부 불가로 바뀌어도 중지할 수 있도록 유지한다.
- 감시 상태는 채널 ID 목록을 한 번에 조회하고, 관리자 화면은 각 행의 pending/error 상태를 분리해 한 행의 실패가 다른 행을 막지 않도록 했다.
- 감시 목록은 `creator`와 `youtube_channel_watch`를 조인해 공개·활성·외부 가용 후보 또는 기존 Watch 소유자만 DB에서 선별한다. 후보 `COUNT(*)`와 정렬·`LIMIT/OFFSET`을 조회 경계에서 수행해 페이지 행의 상태만 읽는다. 전체 수는 마지막 페이지를 넘어간 요청에서도 유지되도록 별도 count 조회로 가져온다. 따라서 공개 목록에서 빠진 기존 Watch도 목록에 남아 중지할 수 있다.
- 프런트 토글은 시작/재시작 가능 여부와 기존 감시 중지 가능 여부를 분리했다. 접근 불가한 `RENEWAL_FAILED`도 중지 요청은 보낼 수 있고, 공개·외부 가용 상태일 때만 재시작한다.
- 현재 Prompt `P7`을 유지하면서 과거 Prompt `P1`~`P6` 작업·Snapshot의 역사적 보존 계약을 복원했다.
- V4 대상 Flyway 검증은 V4 컬럼만, 최신 V5 검증은 `last_error_at`까지 기대하도록 분리했으며 Prompt 문서 계약을 P7로 맞췄다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `./gradlew.bat compileJava compileTestJava --no-daemon --console=plain` | 통과 | 백엔드와 테스트 컴파일 |
| `./gradlew.bat test --tests "com.masiton.ai.application.YoutubeChannelWatchManagementServiceTest" --tests "com.masiton.ai.presentation.AdminYoutubeChannelWatchControllerApiTest" --no-daemon --console=plain` | 통과 | Creator 조건, 상태 응답·`lastErrorAt`, Controller 계약 |
| `./gradlew.bat test --tests "com.masiton.security.SecurityBoundaryApiTest" --no-daemon --console=plain` | 실행 차단 | Docker 데몬 부재로 Testcontainers 초기화 실패 |
| `npm test` | 통과 | 243개 테스트 |
| `npm run typecheck` | 통과 | TypeScript 검사 |
| `npm run build` | 통과 | Next.js 프로덕션 빌드와 `/admin/ai/youtube-channel-watches` 라우트 생성 |
| `npm test` | 통과 | 프런트 전체 244개 테스트, 접근 불가 `RENEWAL_FAILED` 중지 회귀 포함 |
| `npm run typecheck` | 통과 | 토글 상태 계산과 감시 화면 TypeScript 검사 |
| `git diff --check` | 통과 | 공백 오류 없음 |
| GitHub Actions CI #32094957568 | 통과 | Backend 1,290개 테스트와 프런트 타입 검사·프로덕션 빌드가 통과했다. V4 대상 테스트의 V5 `last_error_at` 혼입, V5 컬럼 순서 기대 오류, Prompt 문서 P2 불일치를 순차 수정했다. |

## 8. 재발 방지 및 다음 확인

- 상태별 토글 동작과 `lastErrorAt` 응답 정규화를 프런트·백엔드 회귀 테스트로 고정했다.
- API 추적표에 정확한 GET·PUT 보안 경계 테스트를 연결했다.
- 다중 목록 응답·배치 조회·기존 비공개 감시 행 유지·행별 실패 격리를 API/서비스/프런트 테스트와 계약 문서에 연결했다.
- Docker가 제공되는 CI에서 `SecurityBoundaryApiTest`와 PostgreSQL `JdbcYoutubeChannelWatchStoreIntegrationTest`를 다시 확인한다. 현재 로컬 Docker 데몬이 없어 full-context 통합 실행은 차단되어 있다.
- Docker가 제공되는 CI에서 Creator·Watch 조인 페이징의 기존 Watch 보존, 제외 대상, `COUNT(*) OVER()` 전체 수와 페이지 경계를 확인한다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| 실패 감시 재시작 가능 여부 | 불가 | `RENEWAL_FAILED` 상태 토글 회귀 테스트 | 배포 후 관리자 화면에서 동일 상태 확인 | 코드·테스트 기준 가능으로 변경, 운영 수치는 미측정 | WS-15 담당자, 배포 후 운영 Sandbox 확인 |
| 오류 시각 조회 가능 여부 | 범주만 제공 | API·Store 통합 테스트로 `last_error_at` 확인 | 배포 후 오류 기록 시각과 화면 표시 비교 | 로컬 DB 통합 실행은 Docker 제약, CI에서 확인 | WS-15 담당자, CI 및 Sandbox 확인 |
| 동시 감시 조작 가능 유튜버 수 | 1명 | collection 목록과 독립 행 mutation | 배포 후 관리자 화면에서 복수 채널 확인 | 코드상 다중 행·독립 pending·DB 페이징 구현, 운영 수치는 미측정 | WS-15 담당자, 배포 후 운영 Sandbox 확인 |

## 10. 남은 사항

- 로컬 Docker 데몬 부재로 full-context 보안 테스트와 PostgreSQL 통합 테스트를 실행하지 못했다. 코드 컴파일과 비컨테이너 테스트는 통과했다.
- 기존 12개와 이번 후속 4개 수정 필요 스레드의 반영 내용·검증 범위를 답글로 남기고 해결 처리한다. 이미 반영된 2개 스레드는 기존 CI 근거로 확인 처리한다.
- 로컬 Docker 데몬 부재로 PostgreSQL 조인 페이징 통합 실행은 원격 CI에서 최종 확인한다.
