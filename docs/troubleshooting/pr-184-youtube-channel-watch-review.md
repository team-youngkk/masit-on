---
related_documents:
  - ../01-requirements/functional-requirements.md
  - ../04-product/prd/admin/ai-video-information-extraction.md
  - ../05-specs/api/admin/ai-video-extraction-api.md
  - ../05-specs/data/third-expansion-ai-video-data-contract.md
  - ../06-architecture/dependency-rules.md
  - ../08-planning/third-expansion-implementation-plan.md
  - ../../src/main/java/com/masiton/ai/application/AiExtractionJobService.java
  - ../../src/main/java/com/masiton/ai/infrastructure/persistence/JdbcYoutubeChannelWatchStore.java
  - ../../src/main/java/com/masiton/member/infrastructure/redis/RedisMemberSessionStore.java
  - ../../src/test/java/com/masiton/security/infrastructure/redis/RedisRefreshTokenStoreIntegrationTest.java
---

# PR #184 리뷰 트러블슈팅: YouTube 채널 감시 상태·동시성 경계

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [PR #184](https://github.com/team-youngkk/masit-on/pull/184) |
| 작성자 | @w00lam |
| 처리 일자 | 2026-08-12~2026-08-13 |
| 범위 | 채널 감시 활성화 상태, Webhook 행 잠금, Creator 오류 경계, 비활성 challenge, API 오류 계약, Redis 시간 의존 회귀 테스트 및 활성화→challenge Token 경로 |
| 주 문제 유형 | 애플리케이션 / 데이터베이스 / 인프라 |
| 기존 기록 | [PR #178 통합 회귀 리뷰 기록](pr-178-third-expansion-integration-review.md)을 확인했으며, 이번 PR의 채널 감시 상태·잠금 문제와 직접 겹치는 항목은 없어 신규 기록으로 남긴다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [구독 확인 전 ACTIVE 금지](https://github.com/team-youngkk/masit-on/pull/184#discussion_r3765810013) | 외부 구독 확인 전 ACTIVE 저장 및 Webhook 허용 금지 | 애플리케이션 | 수정 필요 | 활성화 직후 `UNKNOWN`, challenge 성공 후에만 `ACTIVE` 전환 | `AiExtractionJobServiceTest`, 저장소 통합 테스트 |
| [행 잠금 대기 상한](https://github.com/team-youngkk/masit-on/pull/184#discussion_r3765866113) | `FOR UPDATE` 대기의 무한 대기 방지 | 데이터베이스 | 수정 필요 | JDBC statement timeout과 Spring transaction timeout을 5초로 설정 | 저장소 동시성 통합 테스트 |
| [동시성 회귀 테스트](https://github.com/team-youngkk/masit-on/pull/184#discussion_r3765866120) | 실제 동시 트랜잭션으로 행 잠금 검증 | 데이터베이스 | 수정 필요 | `CountDownLatch`·`ExecutorService` 기반 PostgreSQL 통합 테스트 추가 | `JdbcYoutubeChannelWatchStoreIntegrationTest` |
| [Creator 오류 중복](https://github.com/team-youngkk/masit-on/pull/184#discussion_r3765866122) | `CREATOR_NOT_FOUND` 생성 로직 공유 | 애플리케이션 | 수정 필요 | `CreatorReferenceExceptionFactory`로 Creator 관련 호출부를 통합 | 컴파일·ArchUnit 통과 |
| [알림 시각 SQL 조건 중복](https://github.com/team-youngkk/masit-on/pull/184#discussion_r3765866126) | Java 상태 확인과 중복되는 SQL 조건 제거 | 데이터베이스 | 수정 필요 | `markNotificationReceived`는 잠금·Java 판정 후 시각만 갱신 | 단위·저장소 통합 테스트 |
| [API 오류 계약 미동기화](https://github.com/team-youngkk/masit-on/pull/184#pullrequestreview-4915835933) | `CREATOR_NOT_FOUND`를 오류 표에 추가 | 기타 (계약 문서 동기화) | 수정 필요 | API 3.6 상태 흐름과 오류 표 갱신 | `git diff --check` 통과 |
| [Creator Application 내부 참조](https://github.com/team-youngkk/masit-on/pull/184#discussion_r3766088937), [공개 Port 경계](https://github.com/team-youngkk/masit-on/pull/184#discussion_r3766111422) | 다른 도메인이 Creator Application 루트 구현을 직접 import하지 않도록 수정 | 애플리케이션 | 수정 필요 | Creator 오류 팩토리를 `creator.application.port.in` 공개 경계로 이동하고 참조를 치환 | `ArchitectureTest`, 컴파일 통과 |
| [비활성 Watch challenge](https://github.com/team-youngkk/masit-on/pull/184#discussion_r3766088942) | 비활성화 뒤 기존 Token으로 challenge를 성공시키지 않도록 차단 | 애플리케이션 | 수정 필요 | `enabled=false` Watch는 Token 일치 여부와 무관하게 403으로 거부하고 회귀 테스트 추가 | `AiExtractionJobServiceTest` 통과 |
| [Redis 시간 의존 CI 실패](https://github.com/team-youngkk/masit-on/pull/184#discussion_r3766107081) | 고정 과거 시각 때문에 Redis `TIME` 기준 만료 세션이 복구 큐에 적재되지 않음 | 인프라 | 수정 필요 | 테스트 기준 시각을 실행 시점보다 하루 뒤로 설정해 Redis 서버 시계와 만료 경계를 분리 | `RedisRefreshTokenStoreIntegrationTest` 통과 |
| [활성화 Token 저장 경로](https://github.com/team-youngkk/masit-on/pull/184#discussion_r3766428856), [동일 P1 재제기](https://github.com/team-youngkk/masit-on/pull/184#discussion_r3766571023), [Token 해시 미저장](https://github.com/team-youngkk/masit-on/pull/184#discussion_r3766638006) | 활성화부터 challenge까지 실제 Token 생성·해시 저장·외부 전달 경로가 없음 | 애플리케이션 / 데이터베이스 / 외부 연동 | 수정 필요 | 검증 Token Port와 SecureRandom Adapter, PubSubHubbub 구독 Port·Adapter를 연결하고 활성화 시 원문 없이 SHA-256 해시만 저장. WireMock에서 동일 Token 전달을 검증하고 저장된 해시로 `verifyChallenge` 성공까지 회귀 테스트 | `YoutubeChannelWatchManagementServiceTest`, `JdbcYoutubeChannelWatchStoreIntegrationTest`, `PubSubHubbubYoutubeChannelWatchSubscriptionAdapterWireMockIntegrationTest` 통과 |
| [활성화 회귀](https://github.com/team-youngkk/masit-on/pull/184#discussion_r3771463100) | 이미 `ACTIVE`인 채널의 중복 활성화가 상태를 `UNKNOWN`으로 되돌리고 검증 Token을 교체함 | 애플리케이션 / 데이터베이스 | 수정 필요 | `FOR UPDATE`로 기존 유효 구독을 확인하고 `ACTIVE` 상태·해시를 보존하며 외부 재구독을 생략 | `prepareActivation_기존ACTIVE중복활성화_기존상태와토큰해시를유지한다` 통과 |
| [구독 실패 상태·범위](https://github.com/team-youngkk/masit-on/pull/184#discussion_r3771627321), [외부 호출 순서](https://github.com/team-youngkk/masit-on/pull/184#discussion_r3771633348) | Hub 실패 시 상태·오류 범주 미기록, 외부 호출 전에 `UNKNOWN` 행이 커밋됨 | 애플리케이션 / 외부 연동 / 데이터베이스 | 수정 필요 | Hub 호출을 먼저 수행하고 성공한 경우에만 `UNKNOWN + 해시`를 저장. 실패 시 기존 Watch는 `RENEWAL_FAILED + SUBSCRIPTION_*`로 기록하고 신규 Watch는 저장하지 않음. 4xx·5xx·timeout 회귀 테스트 추가 | 관리 서비스·PostgreSQL·WireMock 테스트 통과 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: 외부 challenge 전에도 `subscriptionStatus=ACTIVE`가 반환됨. 비활성 Watch도 Token 일치 시 challenge를 200으로 반환함. 잠금 대기에는 별도 timeout이 없었고, Redis 통합 테스트가 복구 큐 빈 결과로 실패함.
- 발생 환경: Windows, Java 21, PostgreSQL 17.10 Testcontainers, `feature/t-180-youtube-channel-watch`.
- 재현 조건: 신규 Creator에 `enabled=true` 요청, 동시에 같은 채널의 Webhook과 비활성화 요청을 실행한다.
- 실제 결과: 외부 구독 생성·challenge 검증 없이 ACTIVE가 저장될 수 있고, 비활성화한 채널의 challenge가 외부 구독자에게 성공 응답으로 보일 수 있었다. 같은 Watch 행에 대한 요청은 잠금이 끝날 때까지 무제한 대기할 수 있었으며, 고정된 2026-07-29 시각의 세션은 현재 Redis 시각 기준 이미 만료되어 복구 큐 검증이 실패했다.
- 기대 결과: 외부 확인 전에는 Webhook을 받지 않으며, 동시 상태 변경은 유한한 시간 안에 종료되어야 한다.
- 영향 범위: 신규 영상 Webhook의 오접수·미접수, 관리자 API의 상태 오표시, DB 연결 점유 위험.
- 추가 관찰: 수정 전 신규 `setEnabled(true)`는 `subscription_token_hash`가 없는 `UNKNOWN` 행을 저장하고, `verifyChallenge`는 null 해시를 거부했다. 또한 후속 수정에서 이미 `ACTIVE`인 채널의 상태·해시를 새 요청으로 덮어쓰는 회귀와 외부 호출 전 `UNKNOWN` 행 커밋 순서 문제가 생겼다. 현재는 원문 Token으로 Hub 구독을 먼저 성공시킨 뒤 `UNKNOWN + 해시`를 저장하고, 실패 시 기존 Watch만 `RENEWAL_FAILED + SUBSCRIPTION_*`로 기록하며 신규 Watch는 남기지 않는다. 기존 `ACTIVE + 유효 해시`는 그대로 유지한다.

## 4. 근본 원인

활성화 UseCase가 `enabled=true`를 저장하면서 검증 Token을 생성하거나 해시를 저장하지 않아 `verifyChallenge`가 null 해시를 거부하는 단절이 있었다. 외부 구독을 연결한 뒤에는 Hub 호출보다 DB 저장이 먼저 실행되어 실패 시 `UNKNOWN` 행이 남는 순서 문제가 추가로 발견됐다. 또한 Webhook은 감시 행을 `FOR UPDATE`로 잠근 뒤 Job 생성까지 같은 트랜잭션을 유지하면서 DB statement timeout을 설정하지 않았다. Creator 오류는 여러 Application 서비스가 같은 메시지를 각자 생성했고, 이를 공통화한 팩토리를 Creator Application 루트에 두어 다른 도메인이 내부 구현을 직접 참조하게 되었다. `verifyChallenge`는 Token 해시만 확인하고 Watch의 `enabled` 상태를 확인하지 않았다. Redis 테스트는 애플리케이션 주입 Clock만 고정하고 Lua 스크립트가 사용하는 Redis `TIME`은 현재 시각으로 두었다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| API·데이터 계약과 YouTube Webhook 구현의 Token 경로 검색 | 활성화 시 Token 해시를 저장하거나 외부 구독을 요청하는 Adapter가 없음 | 외부 연동을 추측해 추가하지 않고 `UNKNOWN` fail-closed로 수정 |
| `AiExtractionJobService.submitWebhook`와 `JdbcYoutubeChannelWatchStore` 트랜잭션 대조 | Java에서 ACTIVE 판정 후 Job 생성·알림 시각 갱신까지 잠금 유지 | 잠금은 유지하되 statement/transaction timeout을 유한하게 설정 |
| PostgreSQL `FOR UPDATE` 동시 트랜잭션 테스트 추가 | 비활성화 트랜잭션이 선행 Webhook 트랜잭션 종료까지 대기함 | 행 잠금 경계가 실제 DB에서도 유지됨을 회귀 테스트로 고정 |
| Creator 오류 생성 위치 검색 | Creator 조회·Orchestration·감시 서비스에 동일 코드와 메시지가 반복됨 | 공통 Factory를 도입하고 호출부를 치환 |
| 도메인 간 import와 공개 Port 패키지 대조 | AI·Orchestration이 `creator.application` 루트의 팩토리를 직접 참조함 | 팩토리를 `creator.application.port.in`으로 이동해 허용된 공개 경계만 참조 |
| 비활성 Watch의 challenge 단위 재현 | Token 해시가 일치하면 `enabled=false`여도 challenge가 반환됨 | 검증 전에 `enabled`를 확인하고 동일한 403 오류로 fail-closed 처리 |
| Redis Lua 스크립트와 테스트 Clock 대조 | `REVOKE_ALL_SCRIPT`는 Redis `TIME`, 테스트는 2026-07-29 고정 Clock을 사용함 | 실행 시점보다 충분히 미래인 테스트 만료 시각을 사용하고 통합 테스트 재실행 |
| P2 작업 계획·API·데이터 계약 대조 | P2-01은 관리자/Webhook API, P2-02는 YouTube 구독·갱신·검증 Adapter로 분리되어 있음 | #180에 필요한 초기 구독·challenge 검증만 P2-02 범위로 이번 PR에 포함하고, Hub URL·callback URL·실패 범주를 설정과 계약 테스트로 연결. 갱신·만료 스케줄러는 P2-02 후속 범위로 명시 |

## 6. 최종 해결

- 변경 내용: 활성화 상태를 `UNKNOWN`으로 저장하고 challenge 성공 시 `ACTIVE`·`lastRenewedAt`을 기록한다. 비활성 Watch의 challenge는 Token이 일치해도 403으로 거부한다. Creator 오류 팩토리를 `creator.application.port.in` 공개 경계로 이동했다. Webhook 행 잠금 쿼리와 트랜잭션에 5초 상한을 두고, 알림 시각 갱신 SQL의 중복 상태 조건을 제거했다. Redis 레거시 세션 통합 테스트는 실행 시점 기준 미래 만료 시각을 사용한다.
- 이번 재리뷰 반영: 외부 Hub 구독을 `YoutubeChannelWatchSubscriptionPort`·`PubSubHubbubYoutubeChannelWatchSubscriptionAdapter`로 연결했다. 외부 호출을 먼저 성공시킨 뒤 Token 원문은 전달 과정에서만 사용하고 DB에는 SHA-256 해시만 저장한다. 4xx·5xx·timeout은 `SUBSCRIPTION_*` 범주로 정규화하고, 기존 Watch는 `RENEWAL_FAILED`로 기록하며 신규 Watch는 저장하지 않는다. ACTIVE 중복 활성화는 기존 상태·해시를 보존한다.
- 선택 이유: 외부 HTTP 실패 시 핵심 Watch 저장이 0건이어야 하며, 이미 존재하는 Watch에는 관리자가 확인할 수 있는 실패 상태와 오류 범주가 필요하다. 갱신·만료 스케줄러는 #180의 초기 활성화·Webhook 연결 완료 조건에는 포함하지 않고 P2-02 후속 범위로 남긴다.
- 변경 파일: `src/main/java/com/masiton/ai/application/port/out/YoutubeChannelWatchVerificationTokenPort.java`, `src/main/java/com/masiton/ai/infrastructure/security/SecureRandomYoutubeChannelWatchVerificationTokenAdapter.java`, `src/main/java/com/masiton/ai/application/YoutubeChannelWatchManagementService.java`, `src/main/java/com/masiton/ai/application/port/out/YoutubeChannelWatchStore.java`, `src/main/java/com/masiton/ai/infrastructure/persistence/JdbcYoutubeChannelWatchStore.java`, 관련 테스트와 기존 리뷰 반영 파일.
- 고려한 대안: 갱신·만료 스케줄러까지 함께 추가하는 방안은 #180의 관리자 활성화·challenge·Webhook 접수 계약을 넘어서는 범위이므로 이번 PR에서는 제외하고 P2-02 후속 작업으로 남겼다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `./gradlew.bat compileJava compileTestJava --no-daemon --console=plain` | 통과 | 애플리케이션·테스트 컴파일 |
| `./gradlew.bat test --tests "com.masiton.ai.application.YoutubeChannelWatchManagementServiceTest" --tests "com.masiton.ai.presentation.AdminYoutubeChannelWatchControllerApiTest" --tests "com.masiton.ai.application.AiExtractionJobServiceTest" --no-daemon --console=plain` | 통과 | UNKNOWN 활성화, challenge ACTIVE 전환, Webhook 차단, Controller 계약 |
| `./gradlew.bat test --tests "com.masiton.ai.infrastructure.persistence.JdbcYoutubeChannelWatchStoreIntegrationTest" --no-daemon --console=plain` | 통과 | PostgreSQL 상태 보존, 알림 시각, 동시 행 잠금 |
| `./gradlew.bat test --tests "com.masiton.architecture.ArchitectureTest" --no-daemon --console=plain` | 통과 | 계층·패키지 경계 |
| `./gradlew.bat test --tests "com.masiton.security.infrastructure.redis.RedisRefreshTokenStoreIntegrationTest" --no-daemon --console=plain` | 통과 | Redis `TIME`과 레거시 세션 복구 큐 경계 |
| `./gradlew.bat test --tests "com.masiton.ai.application.YoutubeChannelWatchManagementServiceTest" --tests "com.masiton.ai.infrastructure.persistence.JdbcYoutubeChannelWatchStoreIntegrationTest" --tests "com.masiton.ai.infrastructure.external.PubSubHubbubYoutubeChannelWatchSubscriptionAdapterWireMockIntegrationTest" --no-daemon --console=plain` | 통과 | 외부 호출 선행, 구독 실패 범주, 신규 실패 시 저장 0건, 기존 Watch 실패 상태, 4xx·5xx·timeout, Token 해시 저장·challenge 성공 |
| `./gradlew.bat clean build --no-daemon --console=plain` | 시간 초과 | 5분 제한 안에 전체 Testcontainers 빌드가 종료되지 않아 성공 여부를 확인하지 못함 |
| [GitHub Actions CI #31594979628](https://github.com/team-youngkk/masit-on/actions/runs/31594979628) | 통과 | 원격 백엔드 전체 빌드·테스트와 프런트엔드 빌드·타입 검사 |
| `git diff --check` | 통과 | 공백 오류 없음 |

## 8. 재발 방지 및 다음 확인

- 재발 방지: 외부 challenge 전 `UNKNOWN`·Webhook 차단과 비활성 Watch challenge 거부 회귀 테스트, PostgreSQL 동시 잠금 테스트를 추가했다. Creator 도메인 외부 참조는 공개 `application.port.in` 경계로 고정했고, Redis 테스트는 서버 시계와 충돌하지 않는 미래 만료 시각을 사용한다. API 오류 계약 표도 같은 변경에서 갱신한다.
- 다음 확인: 운영 환경의 `YOUTUBE_SUBSCRIPTION_HUB_URL`, `YOUTUBE_WEBHOOK_CALLBACK_URL`, `YOUTUBE_WEBHOOK_SECRET` 값을 주입하고 Hub의 실제 challenge 응답을 운영 Sandbox에서 확인한다. 갱신·만료·재구독 스케줄러는 [P2-02 계획](../08-planning/third-expansion-implementation-plan.md#43-p2-작업-접수와-provider-adapter) 및 [E3-T13 최종 게이트](../08-planning/third-expansion-evaluation-strategy.md) 후속 범위로 추적한다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| 상태 오표시·잠금 대기 정확성 | 해당 없음 (기능·동시성 정합성 문제로 운영 수치 기준 없음) | challenge 전 상태와 동시 트랜잭션 결과를 계약·통합 테스트로 재검증 | 배포 후 Webhook 거부·잠금 timeout 로그를 동일 분류로 확인 | 테스트 기준은 확보했으나 운영 전후 수치 비교는 해당 없음 | WS-15 담당자, 실제 구독 Adapter 연결 시 재검토 |

## 10. 남은 사항

- 실제 YouTube 외부 구독 생성 Adapter는 `PubSubHubbubYoutubeChannelWatchSubscriptionAdapter`로 연결했다. 같은 원문 Token으로 Hub 구독을 먼저 요청하고 성공한 경우에만 DB에 `UNKNOWN + 해시`를 저장하며, challenge 성공 전 Webhook은 계속 차단한다. 실패 시 신규 Watch는 저장하지 않고 기존 Watch는 `RENEWAL_FAILED + SUBSCRIPTION_*`로 기록한다. 갱신·만료 스케줄러와 운영 Sandbox 검증은 P2-02 후속 운영 작업이다.
- 로컬 전체 `clean build`는 5분 제한으로 완료하지 못했지만, 변경을 푸시한 뒤 필수 [GitHub Actions 백엔드·프런트엔드 CI](https://github.com/team-youngkk/masit-on/actions/runs/31594979628)가 모두 통과했다.
- 신규 P1 스레드 3건은 동일 원인으로 묶어 반영했으며, 스레드 답글만 작성하고 해결 처리는 하지 않는다.
