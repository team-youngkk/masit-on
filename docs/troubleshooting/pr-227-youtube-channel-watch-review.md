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
  - ../../src/test/java/com/masiton/security/SecurityBoundaryApiTest.java
---

# PR #227 리뷰 트러블슈팅: YouTube 채널 감시 상태·관리 화면 후속 반영

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [PR #227](https://github.com/team-youngkk/masit-on/pull/227) |
| 작성자 | @inan0226 |
| 처리 일자 | 2026-08-18 |
| 범위 | 채널 감시 재시작 UX, Creator 상태 조회, 보안 증거, 오류 시각 계약, 관리자 화면 중복·선택 상태, 프런트 공용화와 협력자 경계 |
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

## 3. 문제 현상과 발생 조건

- `RENEWAL_FAILED`는 백엔드에서 `enabled=true`로 남을 수 있는데 화면 토글은 `감시 중지`를 표시해 재시도 의도를 만들지 못했다.
- Creator가 감시 활성화 뒤 비공개·외부 이용 불가 상태로 바뀌면 PUT 비활성화는 가능하지만 GET이 404가 되어 결과 확인이 막혔다.
- 요구사항 [FR-AIEXTRACT-006](../01-requirements/functional-requirements.md#fr-aiextract-006-webhook-감시-채널-관리)은 마지막 오류 시각을 요구하지만 기존 테이블·응답에는 범주만 있었다.
- 기존 화면은 `/admin/ai`와 `/admin/ai/youtube-channel-watches`를 동시에 활성화했고, 상태 설명과 같은 값을 상세 목록에서 반복했다.

## 4. 근본 원인

상태 조회·변경 경계가 서로 다른 Creator 검증 조건을 사용했고, 초기 화면 설계가 `enabled`만으로 토글을 결정했다. 또한 V4 스키마에 오류 범주만 있어 오류 시각을 `updated_at`으로 대체하면 다른 갱신 시각과 의미가 섞이는 문제가 있었다. 프런트에서는 기존 Creator 참조·AI 오류 매핑·오류 표현이 감시 기능 안에 다시 작성되었다.

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

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `./gradlew.bat compileJava compileTestJava --no-daemon --console=plain` | 통과 | 백엔드와 테스트 컴파일 |
| `./gradlew.bat test --tests "com.masiton.ai.application.YoutubeChannelWatchManagementServiceTest" --tests "com.masiton.ai.presentation.AdminYoutubeChannelWatchControllerApiTest" --no-daemon --console=plain` | 통과 | Creator 조건, 상태 응답·`lastErrorAt`, Controller 계약 |
| `./gradlew.bat test --tests "com.masiton.security.SecurityBoundaryApiTest" --no-daemon --console=plain` | 실행 차단 | Docker 데몬 부재로 Testcontainers 초기화 실패 |
| `npm test` | 통과 | 242개 테스트 |
| `npm run typecheck` | 통과 | TypeScript 검사 |
| `npm run build` | 통과 | Next.js 프로덕션 빌드와 `/admin/ai/youtube-channel-watches` 라우트 생성 |
| `git diff --check` | 통과 | 공백 오류 없음 |
| GitHub Actions CI #621 | 실패 원인 수정 중 | 전체 1,288개 테스트 중 `Expansion3FlywayMigrationIntegrationTest`의 V5 `last_error_at` 컬럼 기대 목록 누락 1건 확인 |

## 8. 재발 방지 및 다음 확인

- 상태별 토글 동작과 `lastErrorAt` 응답 정규화를 프런트·백엔드 회귀 테스트로 고정했다.
- API 추적표에 정확한 GET·PUT 보안 경계 테스트를 연결했다.
- Docker가 제공되는 CI에서 `SecurityBoundaryApiTest`와 PostgreSQL `JdbcYoutubeChannelWatchStoreIntegrationTest`를 확인한다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| 실패 감시 재시작 가능 여부 | 불가 | `RENEWAL_FAILED` 상태 토글 회귀 테스트 | 배포 후 관리자 화면에서 동일 상태 확인 | 코드·테스트 기준 가능으로 변경, 운영 수치는 미측정 | WS-15 담당자, 배포 후 운영 Sandbox 확인 |
| 오류 시각 조회 가능 여부 | 범주만 제공 | API·Store 통합 테스트로 `last_error_at` 확인 | 배포 후 오류 기록 시각과 화면 표시 비교 | 로컬 DB 통합 실행은 Docker 제약, CI에서 확인 | WS-15 담당자, CI 및 Sandbox 확인 |

## 10. 남은 사항

- 로컬 Docker 데몬 부재로 full-context 보안 테스트와 PostgreSQL 통합 테스트를 실행하지 못했다. 코드 컴파일과 비컨테이너 테스트는 통과했다.
- CI #621에서 확인된 V5 컬럼 기대 목록 누락을 테스트에 반영한 뒤 재실행하고, 통과 후 스레드 답글과 해결 처리를 진행한다.
