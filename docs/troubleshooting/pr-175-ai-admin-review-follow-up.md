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

GitHub inline thread는 조회되지 않아 답글·resolve 대상이 없었다. Docker 기반 동시성 통합 테스트는 CI 환경에서 확인해야 한다.
