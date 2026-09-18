---
related_documents:
  - ../../src/main/java/com/masiton/restaurant/infrastructure/persistence/JdbcRestaurantPlaceRevalidationStore.java
  - ../../src/main/java/com/masiton/restaurant/application/RestaurantPlaceRevalidationStaleException.java
  - ../../src/test/java/com/masiton/restaurant/infrastructure/persistence/JdbcRestaurantPlaceRevalidationIntegrationTest.java
  - ../../src/main/resources/db/migration/V19__add_restaurant_kakao_revalidation.sql
  - ../../src/test/java/com/masiton/FlywayMigrationIntegrationTest.java
  - ./pr-192-flyway-model-contract-review.md
  - ../../.codex/skills/troubleshoot-pr-review/SKILL.md
---

# PR #389 리뷰·CI 트러블슈팅 기록: Kakao 장소 재검증 재시도 예산과 V19 상태 계약

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#389](https://github.com/team-youngkk/masit-on/pull/389) |
| 이슈 | [#377](https://github.com/team-youngkk/masit-on/issues/377) |
| 처리 일자 | 2026-09-18 |
| 범위 | 등록 후 Kakao 장소 재검증 상태 저장, 재시도 예산, V19 통합 테스트 |
| 주요 문제 유형 | 데이터베이스 / 영속성 / CI 테스트 |

## 2. 리뷰 및 CI 현상

| 항목 | 현상 | 분류 |
|---|---|---|
| 리뷰 스레드 `4045957609` | 정상 검증 뒤에도 `attempt_count`가 누적되어 이후 429가 발생하면 재시도 예산을 즉시 소진함 | 수정 필요: 영속성 상태 전이 |
| 리뷰 스레드 `4046003979` | V19 통합 테스트의 한 `UPDATE` 문장에서 `next_attempt_at`을 NULL과 1일 뒤 시각으로 중복 대입함 | 수정 필요: 테스트 SQL |
| 리뷰 스레드 `4046338617` | Restaurant 본문 CAS 실패 후 rollback-only 트랜잭션이 `UnexpectedRollbackException`으로 변환되어 API의 stale 409 처리를 건너뛸 수 있음 | 수정 필요: 트랜잭션·동시성 경계 |
| CI run `35335174496` | 백엔드 1,570건 중 489건 실패. 최초 원인은 V19 테스트의 `RUNNING` 전환 시 `next_attempt_at = NULL`이 `NOT NULL` 제약에 막힌 것 | 수정 필요: 스키마·상태 계약 |
| CI run `35338304823` | V19 수정 후 공통 통합 테스트 cleanup이 재검증 상태·감사 행을 남긴 채 `restaurant`를 삭제해 FK `RESTRICT`에 막힘 | 수정 필요: 테스트 격리 |
| CI run `35338753729` | cleanup 수정 후 V19 감사 INSERT가 `next_attempt_at` 값을 컬럼 목록 없이 전달해 컬럼 수 불일치 | 수정 필요: 테스트 SQL |
| CI run `35339196672` | V19 추가 후 기존 최신 migration 버전 기대값이 18에 고정되어 있었고, append-only trigger의 SQLSTATE가 `DataIntegrityViolationException`이 아닌 일반 `DataAccessException`으로 번역됨 | 수정 필요: 회귀 테스트 기대값 |
| CI run `35339747103` | 최신 수정 반영 후 프론트엔드·Terraform·RSA·백엔드 전체 검증 통과 | 해결 |
| CI run `35340887037` | 새 PostgreSQL 통합 테스트의 Kakao 재검증 mock이 dual-port adapter bean을 대체해 ApplicationContext가 깨짐 | 수정 필요: 테스트 격리 |
| CI run `35341373370` | 통합 테스트가 공유 Testcontainers 데이터베이스에서 다른 full-context 테스트의 cleanup과 경합해 삽입한 Restaurant를 찾지 못함 | 수정 필요: 테스트 실행 격리 |
| CI run `35341754906` | full-context MockMvc 통합 테스트가 공유 DB cleanup 경합으로 404가 재현되어, DB 원자성 검증과 API 계약 검증을 분리할 필요가 확인됨 | 수정 필요: 테스트 경계 |

## 3. 근본 원인

`restaurant_kakao_revalidation`의 상태 제약은 `RUNNING`에서 `next_attempt_at IS NULL`을 요구하고, claim SQL도 lease를 확보하면 해당 값을 NULL로 만든다. 그러나 V19 컬럼 선언은 `NOT NULL`이었다. 두 계약이 동시에 존재해 CI의 첫 실패가 발생했고, Spring 컨텍스트 초기화 실패가 다수 테스트로 전파됐다.

별도로 claim 때 증가한 `attempt_count`를 정상 완료 상태에서 되돌리지 않아, 정기 재검증의 과거 실패가 다음 재검증 주기의 재시도 예산을 잠식했다.

V19 상태 테이블은 의도적으로 Restaurant FK를 `RESTRICT`로 두고 있으므로, 기능 변경으로 추가된 상태·감사 테이블을 기존 공통 테스트 cleanup 목록에 포함하지 않은 것이 두 번째 CI 실패의 원인이었다. 감사 테이블은 append-only라 일반 `DELETE`가 불가능하므로 테스트 격리에서는 상태 테이블과 함께 `TRUNCATE`해야 한다.

## 4. 최종 수정

- `JdbcRestaurantPlaceRevalidationStore.apply`에서 `VERIFIED`, `AUTO_CORRECTED`, `REVIEW_REQUIRED`, `MATCH_NOT_FOUND`로 완료하면 `attempt_count`를 0으로 초기화했다.
- `RETRY_SCHEDULED`와 `RETRY_EXHAUSTED`는 현재 주기의 시도 횟수를 유지해 bounded retry 정책을 보존했다.
- V19의 `next_attempt_at`을 nullable로 변경해 `RUNNING`·`RETRY_EXHAUSTED` 상태 제약과 claim SQL을 일치시켰다.
- V19 통합 테스트의 중복 `next_attempt_at` 대입을 제거했다.
- 공통 통합 테스트 cleanup에서 재검증 상태·감사 테이블을 먼저 `TRUNCATE`해 `restaurant` FK `RESTRICT`를 보존하면서 테스트 간 격리를 회복했다.
- V19 감사 INSERT의 컬럼 목록에 `next_attempt_at`을 명시해 상태 감사 계약과 입력 값을 일치시켰다.
- 최신 migration 버전 기대값을 V19까지 확장하고 append-only 변조 검증은 Spring의 공통 `DataAccessException` 계층으로 검사하도록 조정했다.
- Restaurant 본문 CAS 실패는 전용 `RestaurantPlaceRevalidationStaleException`으로 즉시 rollback하고, application service가 이를 `STALE_DISCARDED`로 변환하도록 수정했다. 실제 PostgreSQL 기반 API 통합 테스트에서 409 응답, `RUNNING` 상태 유지, 감사 행 0건, 동시 변경 본문 보존을 검증한다.
- 실제 PostgreSQL 검증은 공유 full-context cleanup 경합을 피하도록 JDBC store·transaction 통합 테스트로 분리했고, 409 응답 계약은 `AdminRestaurantPlaceRevalidationControllerApiTest`에서 독립적으로 검증한다.

## 5. 검증

| 검증 | 결과 | 비고 |
|---|---|---|
| `gradlew.bat compileJava compileTestJava --no-daemon --console=plain` | 통과 | 컴파일 성공, 기존 varargs 경고 1건 |
| 관련 단위 테스트 3개 클래스 | 통과 | 서비스·컨트롤러·Kakao 어댑터 총 23건 |
| `gradlew.bat test --tests com.masiton.FlywayMigrationIntegrationTest` | 로컬 실행 불가 | Docker Desktop 엔진에 연결할 수 없어 Testcontainers 초기화 실패 |
| `gradlew.bat test --tests com.masiton.restaurant.infrastructure.persistence.JdbcRestaurantPlaceRevalidationIntegrationTest` | 로컬 실행 불가 | Docker Desktop 엔진에 연결할 수 없어 Testcontainers 초기화 실패. 테스트 소스는 컴파일 통과 |
| 관련 단위 테스트 2개 클래스 | 통과 | stale 변환·409 응답 포함 총 9건 |
| `git diff --check` | 통과 | 공백 오류 없음 |
| PR CI 재실행 `35338304823` | 실패 | 스키마 오류는 해소됐고, 공통 cleanup의 FK 정리 누락이 새 원인으로 확인됨 |
| PR CI 재실행 `35338753729` | 실패 | cleanup은 통과했고, 감사 INSERT의 컬럼 수 불일치가 새 원인으로 확인됨 |
| PR CI 재실행 `35339196672` | 실패 | V19 버전 기대값과 append-only 예외 타입 기대값이 기존 테스트에 남아 있는 것을 확인함 |
| PR CI 재실행 `35339747103` | 통과 | 프론트엔드·Terraform·RSA 검사와 백엔드 빌드·자동화 테스트 전체 통과 |
| PR CI 재실행 `35340166251` | 통과 | stale 충돌 처리와 PostgreSQL 통합 테스트가 포함된 최신 커밋 검증 통과 |
| PR CI 재실행 `35340887037` | 실패 | dual-port adapter mock 설정 오류로 통합 테스트 ApplicationContext 초기화 실패 |
| PR CI 재실행 `35341373370` | 실패 | 통합 테스트 데이터가 공유 full-context cleanup과 경합해 404가 발생함 |
| PR CI 재실행 `35341754906` | 실패 | 동일한 공유 full-context DB 경합이 MockMvc 통합 테스트에서 재현됨 |

## 6. 재발 방지

- 상태 제약을 추가하거나 claim SQL을 변경할 때는 상태별 nullable 계약과 실제 전이 SQL을 함께 검증한다.
- 재시도 횟수는 장기 상태의 누적 값인지, 한 실행 주기의 budget인지 명확히 정하고 terminal outcome 전환 테스트에 초기화 여부를 포함한다.
- CI가 다수 테스트를 실패시키면 첫 번째 데이터베이스 제약 오류를 기준으로 원인을 분리하고, 후속 컨텍스트 오류를 독립 결함으로 중복 처리하지 않는다.

## 7. 투입 전후 비교 지표

| 지표 | 투입 전 | 목표 |
|---|---:|---:|
| 리뷰 미해결 스레드 | 2 | 0 |
| PR #389 백엔드 실패 테스트 | 489 | 0 (최종 CI 달성) |
| 정상 terminal outcome 이후 retry budget | 누적 | 0으로 재설정 |
