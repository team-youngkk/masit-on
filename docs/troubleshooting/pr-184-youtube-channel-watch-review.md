---
related_documents:
  - ../01-requirements/functional-requirements.md
  - ../04-product/prd/admin/ai-video-information-extraction.md
  - ../05-specs/api/admin/ai-video-extraction-api.md
  - ../05-specs/data/third-expansion-ai-video-data-contract.md
  - ../../src/main/java/com/masiton/ai/application/AiExtractionJobService.java
  - ../../src/main/java/com/masiton/ai/infrastructure/persistence/JdbcYoutubeChannelWatchStore.java
---

# PR #184 리뷰 트러블슈팅: YouTube 채널 감시 상태·동시성 경계

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [PR #184](https://github.com/team-youngkk/masit-on/pull/184) |
| 작성자 | @w00lam |
| 처리 일자 | 2026-08-12 |
| 범위 | 채널 감시 활성화 상태, Webhook 행 잠금, Creator 오류 중복, API 오류 계약과 관련된 리뷰 반영 |
| 주 문제 유형 | 애플리케이션 / 데이터베이스 |
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

## 3. 문제 현상과 발생 조건

- 오류 메시지: 외부 challenge 전에도 `subscriptionStatus=ACTIVE`가 반환됨. 잠금 대기에는 별도 timeout이 없음.
- 발생 환경: Windows, Java 21, PostgreSQL 17.10 Testcontainers, `feature/t-180-youtube-channel-watch`.
- 재현 조건: 신규 Creator에 `enabled=true` 요청, 동시에 같은 채널의 Webhook과 비활성화 요청을 실행한다.
- 실제 결과: 외부 구독 생성·challenge 검증 없이 ACTIVE가 저장될 수 있고, 같은 Watch 행에 대한 요청은 잠금이 끝날 때까지 무제한 대기할 수 있다.
- 기대 결과: 외부 확인 전에는 Webhook을 받지 않으며, 동시 상태 변경은 유한한 시간 안에 종료되어야 한다.
- 영향 범위: 신규 영상 Webhook의 오접수·미접수, 관리자 API의 상태 오표시, DB 연결 점유 위험.

## 4. 근본 원인

활성화 UseCase가 `enabled=true`를 외부 YouTube 구독 확인 완료와 동일하게 취급했지만, 이 PR에는 실제 구독 생성 Adapter와 검증 Token 발급 경계가 없었다. 또한 Webhook은 감시 행을 `FOR UPDATE`로 잠근 뒤 Job 생성까지 같은 트랜잭션을 유지하면서 DB statement timeout을 설정하지 않았다. Creator 오류는 여러 Application 서비스가 같은 메시지를 각자 생성하고 있었다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| API·데이터 계약과 YouTube Webhook 구현의 Token 경로 검색 | 활성화 시 Token 해시를 저장하거나 외부 구독을 요청하는 Adapter가 없음 | 외부 연동을 추측해 추가하지 않고 `UNKNOWN` fail-closed로 수정 |
| `AiExtractionJobService.submitWebhook`와 `JdbcYoutubeChannelWatchStore` 트랜잭션 대조 | Java에서 ACTIVE 판정 후 Job 생성·알림 시각 갱신까지 잠금 유지 | 잠금은 유지하되 statement/transaction timeout을 유한하게 설정 |
| PostgreSQL `FOR UPDATE` 동시 트랜잭션 테스트 추가 | 비활성화 트랜잭션이 선행 Webhook 트랜잭션 종료까지 대기함 | 행 잠금 경계가 실제 DB에서도 유지됨을 회귀 테스트로 고정 |
| Creator 오류 생성 위치 검색 | Creator 조회·Orchestration·감시 서비스에 동일 코드와 메시지가 반복됨 | 공통 Factory를 도입하고 호출부를 치환 |

## 6. 최종 해결

- 변경 내용: 활성화 상태를 `UNKNOWN`으로 저장하고 challenge 성공 시 `ACTIVE`·`lastRenewedAt`을 기록한다. Webhook 행 잠금 쿼리와 트랜잭션에 5초 상한을 두고, 알림 시각 갱신 SQL의 중복 상태 조건을 제거했다.
- 선택 이유: 실제 외부 구독 Adapter 계약이 없는 상태에서 ACTIVE를 추측해 저장하면 허위 상태와 오접수를 만들기 때문이다. 외부 Adapter가 제공하는 Token으로 challenge가 성공한 뒤에만 수락 경계를 연다.
- 변경 파일: `src/main/java/com/masiton/ai/application/AiExtractionJobService.java`, `src/main/java/com/masiton/ai/application/YoutubeChannelWatchManagementService.java`, `src/main/java/com/masiton/ai/application/port/out/YoutubeChannelWatchStore.java`, `src/main/java/com/masiton/ai/infrastructure/persistence/JdbcYoutubeChannelWatchStore.java`, `src/main/java/com/masiton/creator/application/CreatorReferenceExceptionFactory.java`, Creator·Orchestration 오류 호출부, `docs/05-specs/api/admin/ai-video-extraction-api.md`, 관련 테스트.
- 고려한 대안: YouTube 외부 구독 요청을 이 PR에 새 Adapter로 추가하는 방안은 endpoint·인증·callback 운영 계약이 확정되지 않아 채택하지 않았다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `./gradlew.bat compileJava compileTestJava --no-daemon --console=plain` | 통과 | 애플리케이션·테스트 컴파일 |
| `./gradlew.bat test --tests "com.masiton.ai.application.YoutubeChannelWatchManagementServiceTest" --tests "com.masiton.ai.presentation.AdminYoutubeChannelWatchControllerApiTest" --tests "com.masiton.ai.application.AiExtractionJobServiceTest" --no-daemon --console=plain` | 통과 | UNKNOWN 활성화, challenge ACTIVE 전환, Webhook 차단, Controller 계약 |
| `./gradlew.bat test --tests "com.masiton.ai.infrastructure.persistence.JdbcYoutubeChannelWatchStoreIntegrationTest" --no-daemon --console=plain` | 통과 | PostgreSQL 상태 보존, 알림 시각, 동시 행 잠금 |
| `./gradlew.bat test --tests "com.masiton.architecture.ArchitectureTest" --no-daemon --console=plain` | 통과 | 계층·패키지 경계 |
| `git diff --check` | 통과 | 공백 오류 없음 |

## 8. 재발 방지 및 다음 확인

- 재발 방지: 외부 challenge 전 `UNKNOWN`·Webhook 차단 회귀 테스트와 PostgreSQL 동시 잠금 테스트를 추가했다. API 오류 계약 표도 같은 변경에서 갱신한다.
- 다음 확인: 실제 YouTube 구독 생성·갱신 Adapter의 endpoint·인증·Token 발급 계약이 확정되면 별도 작업에서 `YoutubeChannelWatchStore` Port와 WireMock 계약 테스트를 연결한다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| 상태 오표시·잠금 대기 정확성 | 해당 없음 (기능·동시성 정합성 문제로 운영 수치 기준 없음) | challenge 전 상태와 동시 트랜잭션 결과를 계약·통합 테스트로 재검증 | 배포 후 Webhook 거부·잠금 timeout 로그를 동일 분류로 확인 | 테스트 기준은 확보했으나 운영 전후 수치 비교는 해당 없음 | WS-15 담당자, 실제 구독 Adapter 연결 시 재검토 |

## 10. 남은 사항

- 실제 YouTube 외부 구독 생성·갱신 Adapter는 endpoint·인증·운영 callback 계약 확정 후 별도 작업으로 남아 있다. 이 PR에서는 외부 확인 전 `UNKNOWN`과 Webhook 차단으로 안전한 상태를 보장한다.
