---
related_documents:
  - ../04-product/prd/admin/ai-video-information-extraction.md
  - ../05-specs/api/admin/ai-video-extraction-api.md
  - ../05-specs/data/third-expansion-ai-video-data-contract.md
  - ../07-adr/integration/ai-001-video-extraction-candidate-boundary.md
  - ../../.codex/skills/troubleshoot-pr-review/SKILL.md
---

# PR #175 리뷰 트러블슈팅: 관리자 AI 검수 동시성·태그 감사 후속

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#175 AI 관리자 조회·사후 보정·롤백 구현](https://github.com/team-youngkk/masit-on/pull/175) |
| 작성자 | inan0226 |
| 처리 일자 | 2026-08-12 |
| 범위 | 동시 검수 stale 보호와 수동 확정 태그의 VisitTag·감사 이력 정합성 |
| 주 문제 유형 | 애플리케이션·데이터베이스 |
| 기존 기록 | [PR #173 AI 후보 자동 등록 리뷰](pr-173-ai-candidate-auto-registration-review.md)의 태그 append-only·원자성 경계를 확인하고 이번 관리자 검수 흐름에 적용했다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [PR #175 후속 독립 리뷰](https://github.com/team-youngkk/masit-on/pull/175) | 동일한 `expectedReviewStatus`를 가진 동시 검수가 모두 커밋될 수 있음 | 데이터베이스 | 수정 필요 | 커밋 트랜잭션에서 부모 작업 행을 먼저 잠그고 최신 Snapshot을 다시 조회하며, override INSERT도 최신 Snapshot만 허용 | 대상 Gradle 테스트·ArchUnit 통과; PostgreSQL 동시성 통합 테스트는 Docker 환경에서 후속 확인 |
| [PR #175 후속 독립 리뷰](https://github.com/team-youngkk/masit-on/pull/175) | 수동 확정 시 변경한 태그만 감사되어 unchanged 태그의 최신 판단이 불일치함 | 데이터베이스 | 수정 필요 | 실제 `VisitTag`로 연결된 모든 태그를 `MANUAL_OVERRIDE`·`ADMIN_OVERRIDE`로 append하고 그 목록을 Snapshot 태그 감사에 사용 | `AdminAiExtractionReviewCommitServiceTest` 통과 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: 없음. 동시 요청은 둘 다 `204`가 될 수 있고, 태그 감사는 응답 성공 후에도 최신 판단과 연결 결과가 달라질 수 있다.
- 발생 환경: PR #175의 `develop` 대상 관리자 AI 검수 API, PostgreSQL V6 AI 스키마.
- 재현 조건: 두 관리자가 같은 `AUTO_BLOCKED` Snapshot을 같은 `expectedReviewStatus`로 동시에 CONFIRM하거나, 후보 태그가 여러 개인 AUTO_BLOCKED 결과에서 일부 코드만 보정해 CONFIRM한다.
- 실제 결과: 두 요청이 같은 기존 Snapshot을 stale 여부 없이 사용하거나, unchanged 태그는 `AUTO_REJECTED` 이력에 남은 채 `VisitTag`만 생성될 수 있다.
- 기대 결과: 하나의 검수만 최신 Snapshot을 MANUAL_OVERRIDE로 전환하고 다른 요청은 409 stale이 되어야 한다. 수동 확정으로 연결된 모든 태그의 최신 감사 판단은 MANUAL_OVERRIDE여야 한다.
- 영향 범위: 중복 정식 등록·상충하는 검수 결과·태그 검색 근거와 감사 이력 불일치.

## 4. 근본 원인

기존 `reviewTarget()`의 Snapshot 행 잠금만으로는 부모 작업과 최신 Snapshot 선택이 하나의 재조회 경계로 고정되지 않았다. 첫 요청이 새 Snapshot을 INSERT해도 기존 Snapshot의 상태는 바뀌지 않으므로, 두 번째 요청이 같은 기존 행을 기준으로 진행할 가능성이 있었다. 또한 `connectConfirmedTags()`가 실제 연결 목록을 반환하지 않고, 호출자가 받은 변경 태그 목록만 `ai_candidate_tag_review`에 append했다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| `gh pr view 175`로 작성자·브랜치·상태 확인 | 현재 사용자가 작성한 OPEN PR이며 현재 브랜치가 PR head와 일치 | 수정·커밋·푸시 범위로 판단 |
| GitHub review thread 목록 조회 | 현재 연결된 inline thread는 없음 | 후속 독립 리뷰 알림을 코드 기준으로 검증하고 별도 댓글·resolve는 생성하지 않음 |
| `reviewTarget()`과 `override()` SQL 대조 | 최신 Snapshot 조건과 부모 작업 잠금이 부족함 | 부모 작업 선잠금, 최신 Snapshot 재조회, override 최신성 조건을 함께 적용 |
| `connectConfirmedTags()`와 `appendTagOverrides()` 대조 | 실제 연결 태그와 감사 입력 목록이 분리됨 | 연결된 태그 목록을 반환해 모든 태그를 MANUAL_OVERRIDE로 append |

## 6. 최종 해결

- `reviewTarget()`에서 `ai_extraction_job` 행을 먼저 `FOR UPDATE`로 잠근 뒤 최신 Snapshot을 다시 조회한다.
- `override()` INSERT에 해당 Snapshot이 작업의 최신 Snapshot인지 확인하는 조건을 추가해 stale 요청은 null을 반환하도록 했다.
- 수동 확정 태그 연결은 실제 `VisitTag` 연결 목록을 반환하고, 모든 연결 태그를 `ADMIN_OVERRIDE`와 `MANUAL_OVERRIDE` 감사로 기록한다.
- 변경 파일: `src/main/java/com/masiton/ai/infrastructure/persistence/JdbcAiExtractionAdminQueryAdapter.java`, `src/main/java/com/masiton/ai/application/AdminAiExtractionReviewCommitService.java`, `src/main/java/com/masiton/ai/application/port/out/AiExtractionAdminQueryPort.java`, `src/test/java/com/masiton/ai/application/AdminAiExtractionReviewCommitServiceTest.java`

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `git diff --check` | 통과 | 패치 형식과 공백 오류 없음 |
| `./gradlew.bat test --tests "com.masiton.ai.application.AdminAiExtractionReviewCommitServiceTest" --tests "com.masiton.ai.application.AdminAiExtractionQueryServiceTest" --tests "com.masiton.ai.presentation.AdminAiVideoExtractionControllerApiTest" --tests "com.masiton.architecture.ArchitectureTest" --no-daemon --console=plain` | 통과 | 20개 대상 테스트, 0 failures |
| PostgreSQL 동시성 통합 테스트 | 미실행 | 로컬 Docker Desktop 엔진 부재; CI Docker 환경에서 실행 필요 |

## 8. 재발 방지 및 다음 확인

- 재발 방지: 검수 커밋은 부모 작업 잠금→최신 Snapshot 재조회→상태 조건부 override 순서를 유지하고, 수동 태그 감사는 실제 연결 결과를 입력으로 사용한다.
- 다음 확인: Docker가 제공되는 CI에서 동일 작업의 동시 CONFIRM 요청 결과가 `204` 1건·`409` 1건이고, unchanged 태그의 최신 감사 결정이 `MANUAL_OVERRIDE`인지 확인한다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| 동시 검수 중복 커밋 | 미측정 | PostgreSQL Testcontainers 동시 요청 시나리오 | CI 실행 예정 | 로컬 환경 제약으로 수치 확정 불가 | 김인안·박진영, PR #175 CI |
| 수동 확정 태그 감사 누락 | 재현 가능한 코드 경로 1개 | 연결 태그 수와 MANUAL_OVERRIDE 감사 수 비교 | 단위 테스트 통과 | 연결 목록과 감사 입력을 동일하게 변경 | PR #175 후속 커밋 |

## 10. 남은 사항

이 문서의 최초 기록 이후 PR #175에 추가된 inline 리뷰 19건을 다시 코드·계약 기준으로 확인했다. 중복 지적은 하나의 수정으로 묶었고, 비블로킹 제안 중 근거가 있는 항목도 반영했다. 답글과 resolve는 원격 브랜치에 수정 커밋을 푸시하고 CI 결과를 확인한 뒤 처리한다. Docker 기반 PostgreSQL 테스트는 로컬 엔진 부재로 CI에서 확인한다.

## 11. 추가 리뷰 반영: 목록 SQL·롤백 소유권·계약 오류·재시도 추적

### 11.1 스레드별 판단

| 스레드 | 문제 유형 | 판단 | 처리 |
|---|---|---|---|
| [3762975863](https://github.com/team-youngkk/masit-on/pull/175#discussion_r3762975863) | 데이터베이스 | 수정 필요 | 목록용 SELECT를 상세 snapshot SELECT와 분리하고 PostgreSQL Adapter 목록 회귀 테스트를 추가했다. |
| [3762984474](https://github.com/team-youngkk/masit-on/pull/175#discussion_r3762984474) | 데이터베이스 | 수정 필요 | `visit_tag.created_from_snapshot_id`를 V7로 추가하고 해당 Snapshot의 AI 태그만 삭제한다. `visitCreated=false` 롤백은 `AIEXTRACT_DUPLICATE_CONFLICT` 409로 거부한다. |
| [3762984476](https://github.com/team-youngkk/masit-on/pull/175#discussion_r3762984476) | 애플리케이션 | 수정 필요 | 상세·재시도·검수 오류를 API 계약의 `AIEXTRACT_JOB_NOT_FOUND`, `AIEXTRACT_RETRY_BLOCKED`, `AIEXTRACT_DUPLICATE_CONFLICT`, `AIEXTRACT_VALIDATION_CONFLICT`로 통일했다. |
| [3762984477](https://github.com/team-youngkk/masit-on/pull/175#discussion_r3762984477) | 애플리케이션 | 수정 필요 | Controller API 테스트의 존재하지 않는 오류 코드를 계약 코드로 교체하고 실제 서비스의 롤백 거부 경로를 단위 테스트했다. |
| [3762984482](https://github.com/team-youngkk/masit-on/pull/175#discussion_r3762984482) | 애플리케이션 | 수정 필요 | 프론트 오류 안내를 HTTP 409 일괄 처리에서 `AdminApiError.code`별 계약 안내로 변경하고 409·422 분기 테스트를 추가했다. |
| [3762984485](https://github.com/team-youngkk/masit-on/pull/175#discussion_r3762984485) | 데이터베이스 | 수정 필요 | 외부 검증 전 조회는 `reviewSnapshot()` 무잠금 경로를 사용하고, 커밋 트랜잭션만 `reviewTarget()` 부모 작업·최신 snapshot 잠금을 사용한다. |
| [3762984487](https://github.com/team-youngkk/masit-on/pull/175#discussion_r3762984487) | 애플리케이션 | 수정 필요 | Controller의 null queryService 우회 생성자와 불필요한 `@Autowired`를 제거했다. |
| [3762984490](https://github.com/team-youngkk/masit-on/pull/175#discussion_r3762984490) | 데이터베이스 | 수정 필요 | 재시도 `reason`을 trim·길이 검증 후 새 작업의 `retry_reason`으로 저장하도록 V7·Port·Service·Store를 동기화했다. |
| [3762984494](https://github.com/team-youngkk/masit-on/pull/175#discussion_r3762984494) | 애플리케이션 | 수정 필요 | Adapter와 QueryService의 압축된 메서드·조건문을 NAVER Java 컨벤션에 맞게 줄바꿈하고 중괄호를 추가했다. |
| [3763035310](https://github.com/team-youngkk/masit-on/pull/175#discussion_r3763035310) | 데이터베이스 | 이미 해결 | 위 목록 전용 SELECT 분리와 PostgreSQL 회귀 테스트로 같은 P1을 함께 해결했다. |
| [3763035313](https://github.com/team-youngkk/masit-on/pull/175#discussion_r3763035313) | 데이터베이스 | 이미 해결 | 위 `retry_reason` 저장 및 입력 검증으로 같은 추적성 지적을 함께 해결했다. |
| [3763035321](https://github.com/team-youngkk/masit-on/pull/175#discussion_r3763035321) | 데이터베이스 | 수정 필요 | 최신 마이그레이션 테스트에서 `assertAiSchemaAndContracts`를 복원하고 V7 존재 여부에 따라 V4/V7 계약을 검증하게 했다. |
| [3763035326](https://github.com/team-youngkk/masit-on/pull/175#discussion_r3763035326) | 데이터베이스 | 수정 필요 | 검수 전 조회는 무잠금 `reviewSnapshot`, 커밋 경계만 잠금 `reviewTarget`을 사용해 무의미한 autocommit 잠금을 제거했다. ROLLBACK/DISCARD의 최신 상태 재검증 왕복은 의도적으로 유지했다. |
| [3763035328](https://github.com/team-youngkk/masit-on/pull/175#discussion_r3763035328) | 데이터베이스 | 수정 필요 | 후보 태그 코드의 ACTIVE 정의를 한 번에 조회해 후보별 `tag_definition` SELECT를 제거했다. |
| [3763035335](https://github.com/team-youngkk/masit-on/pull/175#discussion_r3763035335) | 데이터베이스 | 수정 불필요 | override의 다음 버전 계산은 부모 작업 잠금과 최신성 조건을 같은 원자 SQL에 유지해야 하므로 다른 Adapter의 조회 Port로 분리하지 않았다. |
| [3763035338](https://github.com/team-youngkk/masit-on/pull/175#discussion_r3763035338) | 애플리케이션 | 수정 불필요 | 워커는 typed parsed candidate를 사용하지만 관리자 검수는 저장된 Snapshot JSON을 재검증해야 하므로 매핑 경계를 공유하면 계약·책임이 넓어진다. 이번 범위에서는 중복을 유지했다. |
| [3763035341](https://github.com/team-youngkk/masit-on/pull/175#discussion_r3763035341) | 애플리케이션 | 수정 필요 | override 내부의 감사 INSERT를 `appendManualReviewAudit`로 분리해 동시성 복사와 감사 책임을 나눴다. |
| [3763035344](https://github.com/team-youngkk/masit-on/pull/175#discussion_r3763035344) | 애플리케이션 | 수정 필요 | `permitted` 재할당을 제거하고 CONFIRM/ROLLBACK/DISCARD 허용 전이를 인접한 early-return 분기로 정리했다. |
| [3763035346](https://github.com/team-youngkk/masit-on/pull/175#discussion_r3763035346) | 애플리케이션 | 이미 해결 | null `queryService`를 만드는 단일 인자 생성자를 제거했다. |

### 11.2 근본 원인과 선택 이유

- 목록 Adapter가 상세 조회용 snapshot 컬럼을 목록 LATERAL 별칭에 그대로 적용해, 실제로는 존재하지 않는 컬럼을 PostgreSQL이 해석하게 했다. 목록 응답에 필요하지 않은 JSON 컬럼을 제거하는 별도 SELECT가 변경 범위를 가장 작게 유지한다.
- Visit 재사용 여부와 관계없이 공개 Visit을 private으로 바꾸는 기존 롤백 계약으로는 `visit_tag`의 소유권을 판별할 수 없었다. source만으로 기존 AI·관리자 태그를 지우면 다른 작업의 태그를 침범하므로 Snapshot FK provenance를 선택했다.
- 검수 전 외부 검증 호출은 DB 트랜잭션을 열지 않는 구조여야 하므로 무잠금 pre-read와 커밋 전용 잠금 read를 분리했다.
- 재시도 사유는 API 문서에 이미 필수 입력으로 정의되어 있어 제거하지 않고 작업 행의 nullable `retry_reason`으로 보존했다. 기존 작업은 null로 유지된다.

### 11.3 검증 결과

| 검증 | 결과 |
|---|---|
| `gradlew.bat compileJava compileTestJava` | 통과 |
| AI 관련 단위 테스트 23건 | 통과 |
| 프론트 `node --test lib/admin/ai-video-extractions-coordination.test.ts` | 4건 통과 |
| PostgreSQL Testcontainers Adapter·롤백·Flyway V7 테스트 | 로컬 Docker 엔진 부재로 미실행, CI 확인 필요 |
| 프론트 전체 `npm test` | 기존 의존성/Node strip-only 환경 문제로 3개 파일 실패; 새 AI coordination 테스트는 별도 실행 통과 |

변경 파일은 V7 migration, AI 작업·검수·롤백 Port/Service/Adapter, Controller, 프론트 오류 안내·테스트, 관련 데이터/API 계약 문서와 Flyway 회귀 테스트다. 정량 지표는 기존 PR에서 측정하지 않아 이번에도 추정하지 않았으며, CI에서 목록 SQL 성공·Snapshot 태그 삭제 수·기존 태그 잔존 수를 fixture 기준으로 확인한다.

## 12. 최신 리뷰 반영: V6 계약 기대와 CONFIRM→ROLLBACK provenance

### 12.1 스레드별 판단

| 스레드 | 문제 유형 | 판단 | 처리 |
|---|---|---|---|
| [3763195109](https://github.com/team-youngkk/masit-on/pull/175#discussion_r3763195109) | 데이터베이스 | 수정 필요 | 최신 Snapshot 계약 기대 목록에 V6 등록 플래그 CHECK 4개를 추가하고, V6 미적용 단계에서는 이름 기준으로 제외하도록 보완했다. |
| [3763204542](https://github.com/team-youngkk/masit-on/pull/175#discussion_r3763204542) | 데이터베이스 | 수정 필요 | 위 V6 CHECK 누락과 같은 CI 실패를 재현 로그로 확인했으며, V6/V7 제약 제거를 위치가 아닌 제약명 기준으로 바꿨다. |
| [3763206935](https://github.com/team-youngkk/masit-on/pull/175#discussion_r3763206935) | 데이터베이스 | 수정 필요 | CONFIRM override Snapshot이 원본 등록 Snapshot의 ID를 보존해 ROLLBACK이 원본 `created_from_snapshot_id`를 사용하도록 수정했다. |

### 12.2 원인과 해결

- V6 마이그레이션은 `ai_candidate_snapshot`에 등록 플래그 CHECK 4개를 추가했지만, 최신 단계 계약 테스트의 기대 목록에는 해당 제약이 빠져 있었다. V4 단계와 V6 이상 단계를 `includesV6`로 구분하고, `includesV6=false`일 때 제약명을 필터링하도록 했다.
- CONFIRM은 등록 정보를 최신 Snapshot으로 복사하지만 VisitTag provenance는 최초 등록 Snapshot ID로 저장한다. 이후 ROLLBACK이 최신 MANUAL_OVERRIDE Snapshot ID를 전달해 삭제 대상 태그를 찾지 못하는 문제가 있었다.
- Adapter가 작업 내 등록 정보가 처음 기록된 Snapshot을 오름차순으로 조회해 `registrationSnapshotId`로 반환하고, 커밋 서비스가 해당 ID를 롤백 Port에 전달하도록 변경했다. 원본 ID가 없으면 안전하게 409로 거부한다.

### 12.3 검증

| 검증 | 결과 |
|---|---|
| `gradlew.bat compileJava compileTestJava` | 통과 |
| AI 검수 서비스·QueryService·Controller 대상 테스트 | 통과 |
| 원본 provenance 전달 단위 테스트 | 통과 |
| CONFIRM→ROLLBACK PostgreSQL 통합 테스트 | 통과 |
| GitHub Actions 최신 백엔드 | `31559691332`에서 1094건 중 3건 실패; 아래 12.4의 테스트 fixture 보완 후 재실행 예정 |

### 12.4 CI 후속 실패와 fixture 보완

`V6`·`V7` 추가로 `TRUNCATE ... CASCADE`의 의존 경로가 확장되면서 일부 통합 테스트의 기준 태그가 함께 정리되는 것을 확인했다. 테스트가 Flyway 기준 태그가 항상 존재한다고 가정해 직접 `INSERT ... ON CONFLICT`로 필요한 `MENU_NAENGMYEON`·`OCCASION_SOLO`를 복원하도록 보완했다. 같은 실행에서 확인된 최신 마이그레이션 컬럼 계약에는 V6 등록 ID·생성 여부 컬럼 8개를 추가하고, 구버전 단계에서는 컬럼명 기준으로 제외하도록 수정했다.

로컬에서 다음 세 회귀 테스트를 재실행해 통과를 확인했다.

- 최신 V4~V7 마이그레이션 컬럼 계약
- 여러 태그가 같은 Visit에 연결된 맛집 검색
- 동일한 자연어·직접 필터의 `APPLIED` 응답
