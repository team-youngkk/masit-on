---
related_documents:
  - ../06-architecture/transaction-boundaries.md
  - ../07-adr/quality/test-001-automation-strategy.md
  - ../../.codex/skills/troubleshoot-pr-review/SKILL.md
---

# PR #272 리뷰 트러블슈팅: 공유 테스트 컨테이너의 Redis·관리자 계정 격리

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#272 테스트 컨테이너 싱글톤화 및 스프링 테스트 컨텍스트 캐시 통합](https://github.com/team-youngkk/masit-on/pull/272) |
| 작성자 | `w00lam` |
| 처리 일자 | 2026-08-20 |
| 범위 | 미해결 인라인 리뷰 2건(`jinyp01`)과 구현 후 독립 검토 P2 1건 |
| 주 문제 유형 | 애플리케이션·데이터베이스 — 공유 테스트 상태 정리와 FK 정합성 |
| 기존 기록 | 저장소의 `docs/troubleshooting/`를 Redis 정리·Testcontainers·공유 테스트 컨텍스트 키워드로 검색했으나 같은 증상을 다룬 기존 PR 기록은 확인되지 않았다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거·검증 |
|---|---|---|---|---|---|
| [공유 싱글톤 Redis 전체 초기화](https://github.com/team-youngkk/masit-on/pull/272#discussion_r3819299378) | 공유 Redis에서 `FLUSHDB`·`FLUSHALL`을 호출하지 말고 테스트가 만든 키 범위만 정리 | 애플리케이션 | 수정 필요 | `FullContextIntegrationTest.deleteRedisKeys`를 추가하고 Redis 기능별 접두사만 삭제하도록 9개 테스트의 전역 초기화를 교체했다. | 대상 테스트 묶음 통과, `src/test/java`의 `flushDb`·`FLUSHALL`·`execInContainer` 검색 결과 0건 |
| [admin_account 누적](https://github.com/team-youngkk/masit-on/pull/272#discussion_r3819299384) | 동시성 테스트가 삽입한 `admin_account` 행을 테스트 종료 시 정리 | 데이터베이스 | 수정 필요 | 테스트가 만든 `adminId`와 `tokenId`를 필드로 추적하고 `@AfterEach`에서 `confirmation_token`을 먼저 삭제한 뒤 해당 `admin_account`만 삭제한다. | `ConfirmationTokenPostgreSqlIntegrationTest` 통과, FK 삭제 순서 확인 |
| 구현 후 독립 검토: 같은 접두사 테스트 병렬 실행 | 병렬 실행 시 한 테스트의 접두사 삭제가 같은 접두사를 쓰는 다른 테스트 상태를 지울 수 있음 | 애플리케이션 | 수정 필요 | 공유 테스트 기반과 공유 컨테이너를 직접 참조하는 테스트에 `@ResourceLock("shared-test-infrastructure")`를 적용해 Redis·PostgreSQL 공유 자원 접근을 직렬화했다. | 현재 Gradle 병렬 설정은 비활성이고, 잠금 추가 후 `testClasses`와 리뷰 대상 테스트 10개를 재실행해 확인 |

## 3. 문제 현상과 발생 조건

- Redis 문제의 발생 조건은 PR에서 클래스별 Testcontainers를 `FullContextIntegrationTest`의 프로세스 공유 싱글톤으로 바꾼 뒤에도 기존의 DB 전체 초기화를 유지하는 경우다.
- 이 조건에서 한 테스트의 `FLUSHDB`·`FLUSHALL`이 다른 테스트가 같은 Redis DB에 저장한 세션·rate limit·복구 큐·quota 상태를 모두 삭제한다. 테스트 순서와 컨텍스트 캐시 상태에 따라 간헐적 실패가 발생할 수 있다.
- PostgreSQL 문제의 발생 조건은 `ConfirmationTokenPostgreSqlIntegrationTest`가 공유 PostgreSQL에서 매 실행마다 새 `admin_account`를 만들고 `confirmation_token`만 초기화하는 경우다.
- 기대 결과는 각 테스트가 자신이 소유한 Redis 키와 DB 행만 정리해 다른 테스트의 상태를 건드리지 않는 것이다.

## 4. 근본 원인

컨테이너 수명과 테스트 데이터 수명의 경계를 함께 바꾸지 않은 것이 공통 원인이다. Redis는 클래스 종료와 함께 사라지는 전용 컨테이너에서 스위트 전체가 재사용하는 컨테이너로 바뀌었지만, 정리 코드는 기존 전용 컨테이너를 전제로 한 전체 DB 초기화를 유지했다. 확인 토큰 테스트도 전용 PostgreSQL에서 공유 PostgreSQL로 바뀌었지만, 새로 추가한 `admin_account` 행의 생명주기를 테스트 코드에 연결하지 않았다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| `docs/troubleshooting/`에서 Redis·Testcontainers·공유 컨텍스트·정리 관련 기존 기록 검색 | 같은 증상의 기존 기록 없음 | 이번 PR 기록을 새로 작성 |
| `src/test/java`의 `flushDb`·`FLUSHALL`·`execInContainer` 전수 검색 | 수정 전 전역 초기화 지점 11건 확인 | 모든 지점을 기능별 키 접두사 삭제로 교체 |
| Redis 구현의 실제 키 접두사 확인 | member rate limit, session/recovery, verification, course route quota, map rate limit으로 분리됨 | 테스트별로 해당 접두사만 삭제 |
| 확인 토큰 스키마와 테스트 INSERT 확인 | `confirmation_token.admin_account_id`가 `admin_account`를 참조함 | `confirmation_token` → 해당 `admin_account` 순서로 종료 정리 |
| Gradle `testClasses` | 통과. 기존 `FlywayMigrationIntegrationTest` varargs 경고 1건만 표시 | 대상 테스트 실행 |
| 리뷰 대상 테스트 10개 묶음 실행 | 통과, 실패 0건 | 로컬 수정과 검증을 완료하고 원격 반영 준비 |

## 6. 최종 해결

- `FullContextIntegrationTest.deleteRedisKeys`가 전달받은 패턴의 키만 모아 삭제하도록 추가했다.
- 다음 Redis 테스트의 전체 초기화를 기능별 패턴 삭제로 바꿨다.
  - `auth:member:rate-limit:*`
  - `auth:member:session:revocation:recovery:*`
  - `auth:verification:*`
  - `auth:session:*`
  - `restaurant:course-route:*`
  - `restaurant:map:rate-limit:*`
- `RedisRefreshTokenStoreIntegrationTest` 내부의 시나리오 전환 시점에도 rate limit 키만 삭제하도록 바꿔 세션 상태를 보존한다.
- `ConfirmationTokenPostgreSqlIntegrationTest`는 테스트가 생성한 토큰과 관리자 ID를 추적하고, `@AfterEach`에서 FK 의존 순서에 맞게 삭제한다.
- 공유 테스트 기반과 직접 싱글톤 컨테이너를 참조하는 테스트에는 `@ResourceLock("shared-test-infrastructure")`를 적용해 병렬 실행에서도 공유 Redis·PostgreSQL 정리 작업이 서로 겹치지 않게 했다.
- 전역 `FLUSHDB`·`FLUSHALL`은 테스트 소스에서 제거했다. 기능별 키 접두사를 사용하는 이유는 공유 Redis의 다른 기능 상태를 보존하면서 테스트 자체의 이전 상태만 지우기 위해서다.

주요 변경 파일은 `src/test/java/com/masiton/test/FullContextIntegrationTest.java`, Redis 관련 테스트 8개, `AdminRegistrationJourneyAcceptanceTest`, `ConfirmationTokenPostgreSqlIntegrationTest`다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `./gradlew.bat testClasses` | 통과 | 테스트 소스 컴파일 성공. 기존 varargs 경고 1건은 이번 변경과 무관 |
| `./gradlew.bat test --tests ...` (Redis·PostgreSQL·코스·지도·관리자 인수 테스트 10개) | 통과 | 실패 0건. Redis 상태 격리와 확인 토큰 동시성 테스트 포함 |
| `rg -n "flushDb|FLUSHALL|execInContainer" src/test/java` | 결과 0건 | 공유 Redis 전체 초기화 호출 제거 확인 |
| `./gradlew.bat test` (잠금 추가 전) | 통과, 4분 27초 | 전체 백엔드 테스트 회귀 성공. 잠금은 동작 변경 없이 병렬 실행 보호만 추가했으며, 최종 코드에서는 `testClasses`와 대상 테스트 10개를 다시 통과했다 |
| `git diff --check` | 통과 | 문서·코드 공백과 패치 형식 확인 |

## 8. 재발 방지 및 다음 확인

- 공유 Testcontainers로 전환하는 변경에서는 컨테이너 수명 변경과 함께 테스트 정리 범위를 다시 검토한다.
- 공유 Redis 테스트는 전체 DB 초기화를 사용하지 않고 기능별 키 접두사 또는 별도 테스트 DB를 사용한다.
- 공유 PostgreSQL에서 테스트가 생성한 행은 생성한 식별자를 추적해 `@AfterEach`에서 FK 순서대로 삭제한다.
- 새 Redis 키 접두사를 추가하는 테스트는 정리 패턴도 같은 변경에서 함께 확인한다.
- 공유 컨테이너를 직접 참조하는 새 테스트는 `shared-test-infrastructure` 리소스 잠금을 함께 사용한다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 도입 후 값 | 비교 결과 |
|---|---|---|---|---|
| Redis 전체 초기화 호출 | 11건 | `rg -n "flushDb|FLUSHALL|execInContainer" src/test/java` | 0건 | 공유 Redis 전체 삭제 경로 제거 |
| 확인 토큰 테스트의 관리자 행 정리 | 없음 | `ConfirmationTokenPostgreSqlIntegrationTest` `@AfterEach` 확인 | 테스트 생성 ID 기준 1개 정리 경로 | 테스트 간 `admin_account` 누적 방지 |
| 공유 테스트 자원 병렬 보호 | 없음 | `@ResourceLock` 선언 검색 | 공통 기반 및 직접 참조 테스트에 적용 | 같은 자원에 대한 정리 작업 직렬화 |
| 테스트 실행 결과 | 기존 기준 수치 없음 | 리뷰 대상 테스트 10개 묶음 | 실패 0건 | 기능 회귀 없음 |

성능 지표는 이번 변경의 목적이 아니므로 별도로 측정하지 않았다.

## 10. 남은 사항

로컬 수정과 검증은 완료했으나, 이 작업 시점에는 변경 커밋·푸시와 GitHub 인라인 답글·스레드 해결을 아직 수행하지 않았다. 원격 PR에 코드가 올라간 뒤 두 스레드에 원인·변경 파일·검증 명령·이 기록 링크를 답글로 남기고 상태를 다시 확인해야 한다.
