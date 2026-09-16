---
related_documents:
  - ../01-requirements/functional-requirements.md
  - ../01-requirements/non-functional-requirements.md
  - ../05-specs/api/admin/ai-video-extraction-api.md
  - ../05-specs/data/third-expansion-ai-video-data-contract.md
  - ../05-specs/data/migration-plan.md
  - ../07-adr/adr-backlog.md
  - ../../src/main/java/com/masiton/ai/application/YoutubeChannelBackfillService.java
  - ../../src/main/java/com/masiton/ai/application/AiExtractionJobService.java
  - pr-184-youtube-channel-watch-review.md
---

# PR #385 리뷰 트러블슈팅: 보정 조회의 Cursor·처리 원장·quota

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#385](https://github.com/team-youngkk/masit-on/pull/385) |
| 작성자 | w00lam |
| 처리 일자 | 2026-09-15, 재검증·추가 수정 2026-09-16 |
| 범위 | 미해결 리뷰 6건의 현재 구현 검증, 페이지 상한 재개, Job·원장 원자 저장 |
| 주 문제 유형 | 애플리케이션 / 데이터베이스 / 배포 / 인프라 |
| 기존 기록 | 이 문서의 9월 15일 기록과 [PR #184 Watch 리뷰](pr-184-youtube-channel-watch-review.md)를 먼저 확인했다. 기존 원장 멱등성은 재사용하되 Job 생성과 원장 사이의 별도 커밋 문제를 추가 확인해 이전의 해결 판정을 정정했다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [r4012961603](https://github.com/team-youngkk/masit-on/pull/385#discussion_r4012961603) | 영상 상한에서 Cursor 보존 | 애플리케이션 | 이미 해결 | PR 기존 커밋이 남은 영상 수만 조회하고 `MAX_VIDEOS_PER_RUN`으로 다음 Cursor를 저장함 | 75개 상한·50개 처리 후 25개 조회 서비스 테스트 통과. DB 재개 테스트도 PR CI 통과 |
| [r4013054198](https://github.com/team-youngkk/masit-on/pull/385#discussion_r4013054198) | 자동 실행 주기와 계약 충돌 | 애플리케이션 | 결정 필요 | 자동 생성 여부·주기 합의가 없어 현재 수동 실행 계약을 변경하지 않음 | FR-AIEXTRACT-004, API 3.11, ADR-AUTO-001 대조 |
| [r4013054213](https://github.com/team-youngkk/masit-on/pull/385#discussion_r4013054213) | 호출 전 quota 예약·별도 백필 상한 | 인프라 | 이미 해결 | PR 기존 커밋에 Redis Lua 예약 및 두 API 각각 호출 전 차단이 있음 | Adapter의 quota 예약·차단 테스트 통과. Redis 예약·초과 반환 테스트도 PR CI 통과 |
| [r4013054217](https://github.com/team-youngkk/masit-on/pull/385#discussion_r4013054217) | 활성 backfill의 API key 누락 거부 | 배포 | 이미 해결 | PR 기존 커밋에 flag-secret 매핑과 누락 거부 fixture가 있음 | WSL Ubuntu root 파일 설정 테스트 `App file config runtime: PASS` |
| [r4015589465](https://github.com/team-youngkk/masit-on/pull/385#discussion_r4015589465) | 중간 실패·lease 상실 시 신규·재사용 누계 보존 | 데이터베이스 | 수정 필요 | Job 생성과 원장 기록을 `submitBackfillIfClaimActive`의 동일 트랜잭션으로 이동 | 컴파일·서비스 테스트 통과. 실제 DB 롤백·lease 재확보 회귀 테스트 추가, PR CI에서 실행·통과 |
| [r4016300236](https://github.com/team-youngkk/masit-on/pull/385#discussion_r4016300236) | 페이지 상한도 재개 가능한 사유로 중지 | 애플리케이션 | 수정 필요 | `MAX_PAGES_PER_RUN`과 소유자·lease 조건부 중지, 같은 Cursor 재개, V17 전진 마이그레이션 추가 | 서비스 상한 테스트 통과. PostgreSQL 재개·수동 중지 구분·만료 소유자 거부 테스트 추가, PR CI에서 실행·통과 |

## 3. 문제 현상과 발생 조건

- 페이지당 10개, 페이지 상한 10, 영상 상한 500인 실행은 10페이지 뒤 Cursor가 있어도 기존 `stop()`이 `MANUAL`로 기록했다. 다음 시작은 재개 대상이 아닌 새 실행을 만들어 첫 페이지를 반복했다.
- 기존 V16 영상 원장은 동일 영상 중복 집계는 막았지만, Job 접수 트랜잭션이 끝난 뒤 서비스에서 원장을 별도 기록했다. Job 커밋 직후 프로세스 또는 원장 저장이 실패하면 실제 신규 Job을 잃고 재접수 시 재사용으로 집계할 수 있었다.
- 영상 상한·quota·API key 매핑은 9월 15일 커밋에서 반영되어 있어 중복 수정하지 않았다.
- 운영 주기 자동 보정은 요구사항과 수동 실행 API·ADR이 충돌한다. 코드만으로 자동 실행을 추가하거나 요구사항을 축소할 수 없다.
- 영향 범위는 백필 영상 누락, 반복 조회 비용, 운영 처리 건수의 정확성이다. 공개 조회 계약은 변경하지 않는다.

## 4. 근본 원인

페이지 상한 분기가 수동 중지 함수를 재사용했고 `createOrReuse`가 영상 상한 사유만 재개 대상으로 조회했다. 상한 종류별 상태 전이의 불일치다.

처리 원장의 unique key는 중복만 방지할 뿐 Job과 원장의 별도 트랜잭션 사이 실패를 막지 못했다. `AiExtractionJobService.submitBackfillIfClaimActive`가 이미 활성 run·Watch 잠금과 Job 저장을 하나의 트랜잭션으로 묶고 있으므로 이 경계 안에서 원장·누계도 저장해야 한다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| 최신 PR head와 미해결 원문 스레드 조회 | 6개. 기존 기록의 5개 이후 페이지 상한 리뷰가 추가됨 | 원문별로 판단하며 중복 리뷰 댓글은 새 문제로 세지 않음 |
| 기존 기록·API·데이터·비용·신뢰성 계약 대조 | 원장 멱등성은 이미 있으나 트랜잭션은 분리됨 | 원장을 추가하지 않고 저장 위치만 이동 |
| 기존 영상·quota·배포 수정 확인 | 최신 PR 코드에 존재 | 재검증만 수행 |
| 단위·외부 Adapter 모의·배포 계약 테스트 | 45건 통과 | 실제 제공자 호출 없음 |
| 로컬 PostgreSQL·Redis·Flyway 포함 테스트 | 96건 중 40건이 Docker 초기화 실패 | 코드 assertion 결과로 해석하지 않음. 이후 PR CI 전체 빌드 성공으로 검증 공백 해소 |
| Docker Desktop 기동·시작 로그 확인 | Inference manager의 `dockerInference` 소켓 접근 오류로 엔진 시작 실패 | 초기화·데이터 삭제·설정 변경은 수행하지 않음 |
| WSL Ubuntu root 파일 설정 fixture | PASS | 기존 Windows 미실행 제약을 해소. 운영 비밀정보는 사용하지 않음 |

## 6. 최종 수정 내용

- `YoutubeChannelBackfillService`가 페이지·영상 상한에 도달하면 현재 lease 소유자 조건으로 상한 중지를 요청한다. 페이지 상한은 `MAX_PAGES_PER_RUN`을 사용한다.
- `JdbcYoutubeChannelBackfillRunStore`는 두 상한 사유 모두 저장한 Cursor에서 재개한다. 페이지·스캔 구간 카운트는 초기화하고 영상 원장·신규·재사용 누계는 유지한다. 수동 중지는 재개 대상이 아니다.
- [V17](../../src/main/resources/db/migration/V17__add_youtube_backfill_page_limit_reason.sql)은 기존 CHECK에 페이지 상한 사유를 추가한다. 적용된 V15·V16은 수정하지 않았다.
- `AiExtractionJobService`는 Job 생성·원장·누계를 동일 트랜잭션에서 커밋한다. 원장 저장 예외는 Job 생성까지 롤백한다. 네트워크 조회는 기존대로 이 트랜잭션 밖에 있다.
- API·데이터·마이그레이션 문서와 Flyway 기대 이력을 동기화했다.
- 대안: 페이지 상한을 영상 상한 사유로 기록하면 중지 원인이 부정확해진다. 별도 원장 도입은 기존 V16으로 충분하므로 채택하지 않았다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `gradlew.bat test --tests 'com.masiton.ai.application.YoutubeChannelBackfillServiceTest' --tests 'com.masiton.ai.application.AiExtractionJobServiceTest' --tests 'com.masiton.ai.infrastructure.external.YouTubeChannelVideoQueryAdapterTest' --tests 'com.masiton.deployment.AppRunScriptContractTest' --tests 'com.masiton.deployment.DockerComposeLocalAiContractTest' --console=plain` | 통과 | main·test 컴파일 및 45건, 실패 0건 |
| `JdbcYoutubeChannelBackfillRunStoreIntegrationTest`, `RedisYoutubeChannelBackfillQuotaIntegrationTest`, Flyway 통합 테스트 | PR CI 통과 | 로컬은 Docker 초기화 실패였으나, Linux CI에서 실제 PostgreSQL·Redis 통합 테스트 성공을 확인 |
| `wsl -d Ubuntu -u root -- bash -lc "bash /mnt/c/Users/woo_lam/IdeaProjects/masit-on/deploy/scripts/tests/app-file-config-test.sh"` | 통과 | 활성 flag의 key 누락 거부와 올바른 파일 권한·key 존재 통과 |
| `git diff --check` | 통과 | 공백 오류 없음 |
| 실제 YouTube API·운영 Redis·운영 설정 | 미실행 | 외부 제공자 호출 및 운영 데이터 변경 없음 |

9월 15일 기존 기록은 당시 60건 통과를 보고했다. 이번 검증 근거는 수정 커밋 `12588151921d6946306e911ab9db7f25be29504c`의 [CI 실행 #1121](https://github.com/team-youngkk/masit-on/actions/runs/35070826896)이다. `./gradlew --no-daemon clean build`가 성공했고 새 PostgreSQL 테스트 3건, 기존 영상 상한 재개, Redis quota, Flyway 테스트의 개별 PASS를 로그에서 확인했다. 프론트엔드·Terraform·비밀키 검사도 통과했으며 PR이므로 이미지 게시·운영 배포는 실행되지 않았다.

## 8. 재발 방지 및 다음 확인

- 회귀 테스트: 페이지 상한에서 외부 호출 0회, 동일 Cursor 재개, 수동 중지 구분, 만료·다른 lease 소유자의 중지 거부.
- 회귀 테스트: 원장 CHECK 실패를 주입해 Job 0건·누계 0건 롤백을 확인하고, 재접수는 신규 1건으로 확인한다. lease 재확보 후 같은 영상을 다시 접수해도 신규 1건·재사용 0건을 유지해야 한다.
- 통합 검증은 PR CI #1121에서 완료했다. 실제 운영 quota 수치와 자동 실행 계약의 후속 확인은 #375·PR #385로 추적한다.
- 자동 실행 여부와 주기는 WS-15 소유자 김인안 및 요구사항·운영 소유자 합의가 필요하다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준 | 측정 방법 | 도입 후 결과 | 담당자·확인 시점 |
|---|---|---|---|---|
| 페이지 상한 후 외부 호출·재개 Cursor | 수동 중지로 첫 페이지 반복 가능 | 10페이지 상한 서비스 테스트·DB 재개 fixture | 외부 호출 0회와 DB Cursor 보존 재개 통과 | PR 작성자, CI #1121 검증 완료 |
| 원장 저장 실패 후 Job·누계 | 분리 커밋으로 Job만 남을 수 있음 | 원장 CHECK 실패 주입·Job 및 누계 조회 | Job·누계 0건 롤백 및 신규 재접수 통과 | PR 작성자, CI #1121 검증 완료 |
| 활성 backfill의 key 누락 검출 | 최초 리뷰 당시 누락 허용 | WSL root 파일 설정 fixture | 누락 거부·정상 key 통과 확인 | 이번 재검증 완료 |

운영 오류율·비용 수치는 측정하지 않았다. 위 수치는 자동화 fixture의 기능 검증이며 운영 효과로 일반화하지 않는다.

## 10. 남은 사항

- 배포 API key 스레드는 [검증 답글](https://github.com/team-youngkk/masit-on/pull/385#discussion_r4023581418)을 게시하고 해결 처리했다. 자동 실행 스레드는 [결정 요청 답글](https://github.com/team-youngkk/masit-on/pull/385#discussion_r4023584284)을 게시하고 미해결로 유지했다. 이 두 답글은 CI 통합 검증 전에 게시했다. 최종 스레드 상태는 PR에서 확인한다.
- 로컬 Docker 기동 오류는 남아 있지만 PostgreSQL·Redis·Flyway 검증은 CI #1121에서 완료했다. 실제 운영 제공자 호출·quota 수치 검증은 수행하지 않았다.
- 나머지 영상 상한·quota·처리 원장·페이지 상한 4개 스레드는 원격 코드와 통합 검증을 확인해 해결 처리할 수 있다. 자동 실행 계약 충돌은 소유자 결정이 필요하다.
