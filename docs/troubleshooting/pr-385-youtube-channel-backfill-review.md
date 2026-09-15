---
related_documents:
  - ../01-requirements/functional-requirements.md
  - ../01-requirements/non-functional-requirements.md
  - ../05-specs/api/admin/ai-video-extraction-api.md
  - ../05-specs/data/third-expansion-ai-video-data-contract.md
  - ../05-specs/data/migration-plan.md
  - ../07-adr/adr-backlog.md
  - ../../src/main/java/com/masiton/ai/application/YoutubeChannelBackfillService.java
  - ../../src/main/java/com/masiton/ai/infrastructure/redis/RedisYoutubeChannelBackfillQuota.java
  - pr-184-youtube-channel-watch-review.md
---

# PR #385 리뷰 트러블슈팅: YouTube 채널 보정 조회의 Cursor·quota·비밀 설정

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#385](https://github.com/team-youngkk/masit-on/pull/385) |
| 작성자 | w00lam |
| 처리 일자 | 2026-09-15 |
| 범위 | YouTube 채널 보정 조회의 영상 상한 Cursor 보존, 자동 주기 실행 범위, provider/job·백필 quota, 파일 기반 API key 검증, 페이지 중간 누계 보존 리뷰 5건 |
| 주 문제 유형 | 애플리케이션 / 인프라 / 배포 |
| 기존 기록 | [PR #184 YouTube 채널 Watch 리뷰](pr-184-youtube-channel-watch-review.md)를 먼저 확인하고, 기존의 활성 Watch 재확인·lease·외부 호출 격리 원칙을 재사용했다. 자동 주기 실행은 [ADR-AUTO-001](../07-adr/adr-backlog.md)과 [FR-AIEXTRACT-004](../01-requirements/functional-requirements.md)를 함께 대조했다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [r4012961603](https://github.com/team-youngkk/masit-on/pull/385#discussion_r4012961603) | 영상 상한으로 페이지를 중지할 때 현재 Cursor를 잃어 첫 페이지를 반복하지 않도록 재개 위치를 보존 | 애플리케이션 | 수정 필요 | 남은 영상 수만큼 `maxResults`를 줄여 처리하고, 다음 Cursor와 `MAX_VIDEOS_PER_RUN`을 저장한 뒤 다음 명시적 실행에서 재개 | `YoutubeChannelBackfillServiceTest`, `JdbcYoutubeChannelBackfillRunStoreIntegrationTest` 통과 |
| [r4013054198](https://github.com/team-youngkk/masit-on/pull/385#discussion_r4013054198) | 활성 Watch에 대한 자동 주기별 다음 Run 생성이 FR과 맞는지 확인 | 애플리케이션 | 결정 필요 | 자동 생성 주기·활성화 여부가 확정되지 않아 구현하지 않고 스레드를 미해결로 유지 | FR-AIEXTRACT-004와 ADR-AUTO-001의 범위 충돌 확인 |
| [r4013054213](https://github.com/team-youngkk/masit-on/pull/385#discussion_r4013054213) | YouTube 호출 전 공유 provider/job quota와 백필 전용 cap을 원자 예약하고 미확인 시 fail-closed | 인프라 / 애플리케이션 | 수정 필요 | Redis Lua로 provider quota와 백필 quota를 함께 예약하고, `channels.list`·`playlistItems.list` 각각 예약 실패 시 외부 호출을 차단 | `YouTubeChannelVideoQueryAdapterTest`, `RedisYoutubeChannelBackfillQuotaIntegrationTest` 통과 |
| [r4013054217](https://github.com/team-youngkk/masit-on/pull/385#discussion_r4013054217) | 파일 설정 테스트가 활성화 플래그와 YouTube API key의 대응을 검증하지 않아 false green | 배포 | 수정 필요 | `YOUTUBE_BACKFILL_ENABLED`와 `masiton.integration.youtube.api-key` 매핑을 추가하고, key 누락 거부·key 존재 통과 fixture를 추가 | `bash -n` 통과, Linux root 전용 전체 fixture는 로컬 미실행 |
| [r4015589465](https://github.com/team-youngkk/masit-on/pull/385#discussion_r4015589465) | 페이지 중간 예외·lease 상실 뒤 이미 접수한 Job의 `submitted/reused` 누계를 보존 | 애플리케이션 | 수정 필요 | run별 영상 처리 원장에 `SUBMITTED/REUSED`를 멱등 기록하고 페이지 완료 시에는 `scanned`만 누적 | `YoutubeChannelBackfillServiceTest`, `JdbcYoutubeChannelBackfillRunStoreIntegrationTest`, Flyway V16 통합 검증 통과 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: 기존 구현은 예외 없이 `STOPPED`가 되었고, API key 누락 fixture도 설정 검증을 통과할 수 있었다. quota 미확인 상태에서는 YouTube 호출 전 차단 근거가 없었다.
- 발생 환경: PR #385의 `codex/feature/t-375-youtube-backfill` 브랜치, Spring Boot Worker, PostgreSQL `youtube_channel_backfill_run`, Redis quota 경계, 파일 기반 운영 설정 검증.
- 재현 조건: 영상 상한 75·YouTube 페이지 크기 50으로 두 번째 페이지를 처리하거나, `YOUTUBE_BACKFILL_ENABLED=true`만 설정한 파일 fixture를 검증한다.
- 실제 결과: 두 번째 페이지가 상한을 넘으면 Cursor 저장 없이 중지되어 다음 명시적 실행이 첫 페이지부터 반복한다. 페이지 첫 Job이 접수된 뒤 다음 영상에서 예외·lease 상실이 발생하면 run 누계에는 앞선 Job이 반영되지 않는다. 활성 Watch는 수동 POST 이후 다음 Run이 자동 생성되지 않는다. API key와 quota 설정이 확인되지 않아도 외부 호출 경계가 없었다.
- 기대 결과: 상한 직전까지만 처리하고 다음 Cursor에서 재개하며, 페이지별 Job 결과와 누계를 같은 멱등 원장에 보존해야 한다. API 호출마다 provider/job quota와 백필 cap을 원자적으로 확인하고, 비밀값·quota가 없으면 fail-closed해야 한다. 자동 주기 실행은 계약과 ADR이 일치해야 한다.
- 영향 범위: 동일 영상 재조회·quota 낭비·실시간 Job starvation, 실제 처리량과 다른 운영 상태, 운영 설정 false green이 발생할 수 있고, 자동 주기 여부를 임의로 바꾸면 MVP 범위와 API·데이터 계약이 달라진다.

## 4. 근본 원인

첫째, 서비스가 YouTube 페이지를 항상 최대 크기로 조회한 뒤 `scannedCount + page.size()`가 상한을 넘으면 페이지 전체를 버렸다. 저장소는 `STOPPED` 상태만 기록하고 Cursor를 재개 대상으로 구분하지 않았기 때문에, 다음 시작은 `page_token=null`로 첫 페이지를 다시 읽었다.

둘째, 외부 YouTube Adapter가 `channels.list`와 `playlistItems.list`를 호출하기 전에 공유 quota를 예약하지 않았다. 기존 Gemini Worker의 DB quota와는 호출 제공자가 달라 YouTube provider/job 예산과 백필 전용 cap을 별도로 원자화할 Redis 경계가 필요했다.

셋째, 파일 설정 검증의 flag-secret 대응표에 YouTube backfill flag가 없었다. fixture는 활성 flag만 추가하고 `masiton.integration.youtube.api-key`를 만들지 않았는데도 검증 결과를 성공으로 기대했다.

넷째, FR-AIEXTRACT-004는 운영 계약의 주기를 요구하는 반면 ADR-AUTO-001과 현재 API·데이터 계약은 자동 주기별 실행 생성을 Post-MVP·명시적 관리자 시작 범위로 남긴다. 주기와 소유자 승인이 없는 상태에서 스케줄러를 추가하는 것은 코드 결함 수정이 아니라 계약 변경이다.

다섯째, Job 접수는 영상마다 별도 트랜잭션으로 끝나지만 `submitted_count`·`reused_count`는 페이지 전체 성공 시 `completePage`에서만 갱신됐다. 페이지 중간 실패 또는 lease 상실이면 이미 접수된 결과가 사라지고, 재시도 시 같은 run에서 다시 집계될 수 있었다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| PR의 원문 inline 스레드와 최신 review summary 대조 | 원문 미해결 스레드는 5건이며 summary review는 이 5건을 재진술한 것이었다. | 원문 5건을 답변·해결 처리 대상으로 삼고 summary에는 중복 답변하지 않음 |
| `functional-requirements.md`, NFR-COST-001, API·데이터 계약, ADR-AUTO-001 대조 | quota fail-closed는 확정 계약이고 자동 주기 실행은 FR과 ADR이 충돌한다. | quota는 구현하고 자동 주기 실행은 `결정 필요`로 남김 |
| 기존 `pr-184-youtube-channel-watch-review.md` 확인 | 활성 Watch 재확인, 외부 호출 격리, lease 복구 원칙이 이미 기록되어 있었다. | 같은 원칙을 backfill Cursor·quota 처리에 적용 |
| Job 접수 트랜잭션과 `completePage` 누계 갱신 경로 확인 | 영상별 Job은 먼저 커밋되지만 페이지 누계는 마지막에만 저장되어 중간 실패 시 불일치가 재현됐다. | run별 영상 원장과 unique 멱등 기록을 추가 |
| `./gradlew.bat compileJava --no-daemon --console=plain` | 통과 | production 소스와 Spring quota bean 컴파일 확인 |
| `./gradlew.bat test --tests "com.masiton.ai.application.YoutubeChannelBackfillServiceTest" --tests "com.masiton.ai.infrastructure.external.YouTubeChannelVideoQueryAdapterTest" --tests "com.masiton.ai.infrastructure.persistence.JdbcYoutubeChannelBackfillRunStoreIntegrationTest" --tests "com.masiton.ai.infrastructure.redis.RedisYoutubeChannelBackfillQuotaIntegrationTest" --tests "com.masiton.Expansion3FlywayMigrationIntegrationTest" --tests "com.masiton.FlywayMigrationIntegrationTest" --tests "com.masiton.deployment.DockerComposeLocalAiContractTest" --tests "com.masiton.deployment.AppRunScriptContractTest" --no-daemon --console=plain` | 통과 | Cursor 경계, PostgreSQL V15·V16, YouTube 호출 quota, Redis 원자 예약과 rollback, 배포 계약을 확인 |
| `git diff --check` 및 변경 셸 `bash -n` | 통과 | patch 공백과 `app-file-config.sh`, `app-run.sh`, `app-file-config-test.sh` 문법 확인 |

## 6. 최종 해결

- 변경 내용: 남은 영상 수를 YouTube `maxResults`로 전달하고, 다음 Cursor를 `MAX_VIDEOS_PER_RUN` 중지 사유와 함께 저장한다. 다음 명시적 시작은 저장된 Cursor에서 재개하며 페이지·영상 상한 집계만 새 구간 기준으로 초기화하고 `submitted/reused` 누계는 영상 원장과 함께 유지한다. 수동 중지는 자동 재개하지 않는다.
- 변경 내용: Redis Lua가 provider/job quota와 백필 전용 quota를 한 번에 예약하고, 어느 한도라도 넘으면 두 counter를 함께 되돌린다. Redis 결과가 확인되지 않거나 저장소가 실패하면 `YOUTUBE_QUOTA_UNAVAILABLE`로 외부 호출을 하지 않는다.
- 변경 내용: 운영 환경변수·Compose·application 설정에 YouTube quota 창과 두 한도를 연결하고, 파일 기반 설정에서 backfill flag가 API key를 요구하도록 매핑과 거부 fixture를 보완했다.
- 변경 내용: V15에 `stop_reason` 열과 CHECK 제약을 추가하고, V16에 run별 영상 처리 원장과 unique·결과 CHECK를 추가해 API·데이터·migration·추적성 문서를 현재 동작과 맞췄다.
- 변경 파일: `src/main/java/com/masiton/ai/application/YoutubeChannelBackfillService.java`, `src/main/java/com/masiton/ai/infrastructure/persistence/JdbcYoutubeChannelBackfillRunStore.java`, `src/main/java/com/masiton/ai/infrastructure/external/YouTubeChannelVideoQueryAdapter.java`, `src/main/java/com/masiton/ai/infrastructure/redis/RedisYoutubeChannelBackfillQuota.java`, `src/main/resources/db/migration/V15__add_youtube_channel_backfill_stop_reason.sql`, `src/main/resources/db/migration/V16__add_youtube_channel_backfill_video_ledger.sql`, `deploy/scripts/app-file-config.sh`, `deploy/scripts/app-run.sh`, 관련 테스트·계약 문서
- 선택 이유: Cursor는 페이지를 버리는 대신 Provider가 지원하는 남은 페이지 크기를 요청해야 데이터 누락이 없고, quota 두 경계는 Redis 단일 script에서 함께 판정해야 동시 요청 사이에 cap이 초과되지 않는다. 자동 주기는 계약 승인 없이 범위를 넓히지 않았다.
- 선택 이유: 영상별 원장은 Job 접수와 누계 기록을 unique key로 연결해 페이지 전체 성공 여부와 무관하게 앞서 처리한 결과를 남기고, lease 재시도 시 중복 증가를 막는다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `./gradlew.bat compileJava --no-daemon --console=plain` | 통과 | main 소스 컴파일 |
| 지정한 Gradle 테스트 8개 클래스 | 통과 | 단위·PostgreSQL·Redis·Flyway·배포 계약 회귀 60건, 실패 0건 |
| `git diff --check` | 통과 | 공백 오류 없음 |
| `bash -n deploy/scripts/app-file-config.sh deploy/scripts/app-run.sh deploy/scripts/tests/app-file-config-test.sh` | 통과 | 셸 문법 |
| `bash deploy/scripts/tests/app-file-config-test.sh` | 미실행 | Linux root 권한과 root 소유·mode fixture가 필요한 테스트이며 현재 Windows 작업 환경에서는 실행하지 않음 |
| 실제 YouTube API·운영 Redis·운영 설정 | 미실행 | 로컬·자동화에서 실제 제공자 호출을 하지 않는 저장소 규칙에 따름 |

## 8. 재발 방지 및 다음 확인

- 재발 방지: 영상 상한 직전 페이지의 남은 크기·Cursor 재개, 페이지 중간 예외의 영상별 누계 보존·재시도 중복 방지, Redis 두 quota cap의 rollback, API key 누락 설정 거부를 각각 자동화 테스트로 고정했다. V15·V16 migration history와 데이터 계약도 함께 검증한다.
- 다음 확인: Linux root 환경에서 `sudo bash deploy/scripts/tests/app-file-config-test.sh`를 실행하고, 운영 quota 한도와 Redis key 보존·TTL을 운영 담당자가 승인한다.
- 다음 결정: FR-AIEXTRACT-004의 “운영 계약에서 정한 주기”의 주기, 자동 Run 생성 여부, ADR-AUTO-001·API·데이터 계약 갱신 여부를 요구사항·WS-15 소유자가 결정한다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| 상한 경계에서 중복 조회되는 페이지 | 두 번째 페이지 전체 폐기 후 첫 페이지 반복 가능 | max 75·page 50 fixture에서 Cursor와 처리 건수 확인 | 자동화에서 25개 잔여 처리·다음 Cursor 보존 | 반복 경로 제거 확인 | WS-15, 다음 운영 backfill 실행 |
| YouTube quota 초과 호출 | 호출 전 공유 reservation 없음 | Redis Lua cap 초과 동시성·rollback fixture | 초과 예약은 false, 외부 HTTP 0회 | fail-closed 경계 확인 | WS-15, 운영 quota 승인 후 |
| 활성 backfill flag의 API key 누락 검출 | 파일 fixture가 누락을 검증하지 않음 | Linux root 파일 설정 계약 테스트 | 누락 거부·key 존재 통과 | false green 방지 | 배포 담당자, CI 실행 시점 |
| 페이지 중간 실패 뒤 run 처리 누계 정확성 | 페이지 완료 전 접수 결과가 누락될 수 있음 | 동일 run의 영상 원장 unique 재시도 fixture | 신규·재사용 각 1건으로 고정 | 누계 보존·중복 방지 | WS-15, 다음 lease 재시도 |

## 10. 남은 사항

- [r4013054198](https://github.com/team-youngkk/masit-on/pull/385#discussion_r4013054198)은 자동 주기 실행의 주기·범위·계약 갱신 결정을 기다리므로 해결 처리하지 않는다.
- [r4015589465](https://github.com/team-youngkk/masit-on/pull/385#discussion_r4015589465)은 영상별 원장과 회귀 테스트를 추가해 해결 대상에 포함한다.
- Linux root 전용 파일 권한 fixture와 실제 운영 YouTube/Redis quota 확인은 CI·운영 환경에서 추가 확인해야 한다.
