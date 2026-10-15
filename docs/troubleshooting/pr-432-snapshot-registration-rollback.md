---
related_documents:
  - ../05-specs/data/third-expansion-ai-video-data-contract.md
  - ../08-planning/issue-419-ai-contract-proposal.md
  - ../../.codex/skills/troubleshoot-pr-review/SKILL.md
---

# PR #432 리뷰 트러블슈팅: 같은 Snapshot의 작업 등록 참조 보존

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [PR #432](https://github.com/team-youngkk/masit-on/pull/432) |
| 작성자 | @tjdgns0618 |
| 처리 일자 | 2026-10-15 |
| 범위 | 같은 Snapshot의 작업 수준 등록 참조를 단위 롤백의 공유 자원 판정에 포함 |
| 주 문제 유형 | 데이터베이스 |
| 기존 기록 | [PR #244 등록 단위 원자성과 보상 롤백](pr-244-registration-unit-atomicity-review.md)을 확인했다. 해당 기록은 롤백 참조와 트랜잭션 경계를 다루지만 같은 Snapshot의 작업 수준 참조 누락은 다루지 않는다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [같은 Snapshot의 등록 참조 보존](https://github.com/team-youngkk/masit-on/pull/432#discussion_r4278167816) | 등록 단위 참조를 CAS로 지운 뒤에도 같은 Snapshot의 `registered_*` 참조가 남으면 공개 자원과 방문 태그를 보존해야 함 | 데이터베이스 | 수정 필요 | 단위 롤백은 같은 Snapshot의 작업 수준 참조를 보존 대상으로 계산하고, 작업 전체 롤백은 같은 Job의 이전/대상 Snapshot 참조를 외부 공유로 오인하지 않도록 범위를 제한. PostgreSQL 회귀 테스트 추가 | `AutoRegisterRestaurantReusePostgreSqlIntegrationTest`의 같은 Snapshot 단위 참조 보존 및 `Expansion3FlywayMigrationIntegrationTest`의 작업 전체 CONFIRM→ROLLBACK 비공개 전환 검증 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: 없음
- 발생 환경: PostgreSQL 통합 테스트, PR #432의 단위 롤백 경로
- 재현 조건: 하나의 Snapshot에 등록 단위와 작업 수준 `registered_*` 참조가 같은 Restaurant·Creator·Video·Visit를 가리키고, 롤백 CAS가 등록 단위 참조를 먼저 비운다.
- 실제 결과: 공유 조회가 같은 Snapshot을 제외하여 남은 작업 수준 참조를 보지 못하고, 생성 자원을 PRIVATE로 바꾸거나 Visit 태그를 삭제할 수 있다.
- 기대 결과: 같은 Snapshot 작업 수준 참조가 남아 있는 동안 공유 자원으로 판단해 공개 상태와 Visit 태그를 보존한다.
- 영향 범위: 등록 단위 롤백의 정식 콘텐츠 공개 상태 및 방문 태그

## 4. 근본 원인

`JdbcAiRegisteredContentStore.isShared()`는 단위 CAS 뒤에도 같은 Snapshot의 작업 수준 등록 참조를 보존해야 한다. 다만 이 조회를 무조건 모든 Snapshot 참조로 넓히면 관리자 확정 후 작업 전체 롤백 때 같은 Job의 원본·검토 Snapshot을 별도 공유 등록으로 오인해 자원이 계속 공개된다. 참조 소유 Job과 롤백 범위(전체 작업 또는 개별 등록 단위)를 함께 고려해야 한다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| 기존 PR #244 트러블슈팅 기록 검색 | 다른 등록 단위 참조 및 보상 경계는 다뤘으나 Snapshot 작업 참조 누락은 없음 | 동일 원인으로 오인하지 않고 별도 회귀 기록 작성 |
| `isShared()`와 기존 CONFIRM→ROLLBACK 회귀 경로 확인 | 모든 Snapshot 참조를 공유로 세면 같은 Job의 원본·검토 Snapshot 참조만으로 전체 롤백의 비공개 처리가 막힘 | 단위 롤백에서 같은 Job 참조를 보호하고, 전체 작업 롤백에서는 다른 Job만 외부 공유로 계산 |
| GitHub PR CI의 Flyway 회귀 2건 재현 | 마이그레이션 목록 기대값이 V20에서 멈췄고, 같은 Job의 검토 Snapshot 참조 때문에 기존 PRIVATE 기대 동작이 깨짐 | V21 기대값 동기화 및 Job 경계가 있는 공유 조회로 수정 |

## 6. 최종 해결

- 변경 내용: 단위 롤백은 같은 Snapshot의 작업 수준 참조를 포함해 보호하고, 전체 작업 롤백은 다른 Job의 Snapshot 참조만 외부 공유로 보호한다. 현재 Job의 원본·검토 Snapshot은 전체 작업 롤백 대상에 속한다. V21 마이그레이션 추가에 맞춰 최신 버전 검증을 21까지 갱신했다.
- 선택 이유: 단위 CAS 후 남은 job-level 참조는 해당 자원의 생존 근거지만, 같은 Job의 과거/검토 Snapshot은 전체 작업 롤백에서 별도 소유자가 아니다.
- 변경 파일: `src/main/java/com/masiton/orchestration/infrastructure/rollback/JdbcAiRegisteredContentStore.java`, `src/test/java/com/masiton/orchestration/application/command/AutoRegisterRestaurantReusePostgreSqlIntegrationTest.java`, `src/test/java/com/masiton/Expansion3FlywayMigrationIntegrationTest.java`

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `./gradlew.bat test --tests '*Expansion3FlywayMigrationIntegrationTest' --tests '*AutoRegisterRestaurantReusePostgreSqlIntegrationTest' --no-daemon --console=plain` | 통과 | PostgreSQL 통합 테스트 24건과 ArchUnit 검사 10건 통과. 같은 Snapshot의 단위 참조 보존, 다른 Job Snapshot이 참조하는 리소스 공개 보존, 동일 Job의 CONFIRM→ROLLBACK 비공개 처리, V21 migration 이력을 검증 |
| `./gradlew.bat clean build --no-daemon --console=plain` | 실패 (환경 제약) | 1,743건 중 3건이 `배포 dependency health 진단 계약`에서 실패했다. 스크립트 테스트가 로컬에서 Python 실행 파일을 찾지 못해 진단 JSON 대신 `Python 백엔드 dependency health JSON 파싱 실패`를 출력했고, 예상 종료 코드 1/2 대신 Windows `9009`를 반환했다. 관련 Gradle 테스트와 코드 변경은 통과했고, 이 수정과 무관한 실행환경 제약으로 분리했다. |

## 8. 재발 방지 및 다음 확인

- 재발 방지: 단위 롤백의 같은 Snapshot 작업 수준 참조와 전체 롤백의 다른 Job 참조를 각각 구성해 공개 상태를 확인하고, 전체 롤백의 동일 Job Snapshot은 PRIVATE 처리되는 기존 PostgreSQL 회귀 테스트로 범위를 구분
- 다음 확인: 없음

## 9. 도입 전후 비교 지표

해당 없음. 이 결함은 참조 무결성과 롤백 상태를 다루며 배포 확장 뒤 비교할 운영 빈도 지표가 이 수정의 효과를 직접 측정하지 않는다.

## 10. 남은 사항

- 없음
