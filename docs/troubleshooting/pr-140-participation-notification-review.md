---
related_documents:
  - ../05-specs/api/notification/notification-api.md
  - ../05-specs/api/participation/submission-report-api.md
  - ../05-specs/api/common/identifier-contract.md
  - ../07-adr/integration/notify-002-in-app-notification-reliability.md
  - pr-128-skill-troubleshooting-authority-review.md
---

# PR #140 리뷰 트러블슈팅: 제보·신고 알림 연결 및 원자성 롤백 테스트 보완

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#140](https://github.com/team-youngkk/masit-on/pull/140) |
| 작성자 | @w00lam |
| 처리 일자 | 2026-08-05 |
| 범위 | PR #140 코드 리뷰 지적 사항 해결 (원자성 롤백 통합 테스트 AOP 적용, README.md 미추적 링크 제거, Enum 매핑 안전화) |
| 주 문제 유형 | 애플리케이션 / 데이터베이스 / Git |
| 기존 기록 | [PR #128 트러블슈팅 기록 권위](pr-128-skill-troubleshooting-authority-review.md) |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [스레드 1](https://github.com/team-youngkk/masit-on/pull/140#discussion_r3708963897) | `TST-E2-ATOMIC-001` 원자성 롤백 통합 테스트 수동 서비스 인스턴스 생성으로 AOP `@Transactional` 미적용 및 autocommit 문제 해결 | 애플리케이션 / 데이터베이스 | 수정 필요 | `@MockitoSpyBean` 적용하여 Spring 컨테이너의 `@Transactional` AOP 프록시 빈 `adminUseCase`를 통해 테스트하도록 전환 | Postgres 통합 실행 시 상태 `RECEIVED` 및 이력 0건 롤백 검증 성공 (`1779b67`) |
| [스레드 2](https://github.com/team-youngkk/masit-on/pull/140#discussion_r3708963898) | `docs/troubleshooting/README.md` 미추적 문서 링크 정리 요청 | Git | 수정 필요 | 브랜치에 미존재하는 `pr-135~139` 깨진 링크 제거 후 `pr-134` 유효 링크로 정리 | Git tracked 상태와 인덱스 링크 일치 확인 (`2531587`) |
| 참고 | `NotificationStatus.valueOf(...)` enum 매핑 안전화 | 애플리케이션 | 수정 필요 | `toNotificationStatus` 명시적 `switch` 패턴 헬퍼 메서드로 전환 | 런타임 타입 안전성 확보 및 단위 테스트 통과 (`2531587`) |

## 3. 문제 현상과 발생 조건

- 오류 메시지:
  ```
  회원 제보·신고 PostgreSQL 통합 > TST-E2-ATOMIC-001: 알림 저장 실패 주입 시 상태 변경과 이력이 함께 롤백된다 FAILED
      org.opentest4j.AssertionFailedError: expected: "RECEIVED" but was: "IN_REVIEW"
  ```
- 발생 환경: PostgreSQL Testcontainers 통합 테스트 환경 (`ParticipationPostgreSqlIntegrationTest`)
- 재현 조건: `new AdminParticipationService(...)` 수동 생성 후 `updateSubmission(...)` 호출
- 실제 결과: `new`로 생성된 서비스 인스턴스에는 Spring AOP `@Transactional` 프록시 인터셉터가 적용되지 않아 `JdbcTemplate` 단에서 개별 autocommit이 발생하여 롤백이 동작하지 않고 상태가 `IN_REVIEW`로 커밋됨
- 기대 결과: 알림 저장 중 예외 발생 시 전 트랜잭션이 롤백되어 제출 상태가 `RECEIVED`로 유지되고 감사 이력이 0건이어야 함
- 영향 범위: ADR-NOTIFY-002 10절이 요구하는 원자성 롤백 통합 검증의 무효화 및 CI 검사 실패

## 4. 근본 원인

1. **Spring AOP 프록시 미적용**: 클래스 상단의 `@Transactional`은 Spring 컨테이너가 생성한 빈(Bean) 프록시에서만 동작함. `new AdminParticipationService(...)`로 직접 생성한 객체는 프록시 래퍼가 없어 트랜잭션 경계가 형성되지 않음.
2. **`MANDATORY` 트랜잭션 전파 규칙 및 인터페이스 스파이 특성**: `CreateNotificationUseCase.create(...)` 진입점에는 `@Transactional(propagation = Propagation.MANDATORY)`가 설정되어 있어, 인터페이스나 `NotificationService` 진입점을 spy할 경우 `when(...)` 스텁 설정 시점에 트랜잭션 밖 호출로 인한 `IllegalTransactionStateException`이 발생하거나 프록시 위임에 문제가 생김.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| `new AdminParticipationService(...)` 직접 수동 생성 | `@Transactional` 미작동으로 PostgreSQL autocommit 발생하여 롤백 실패 | 기각: Spring 컨테이너 빈(`adminUseCase`)을 이용해야 함 |
| `CreateNotificationUseCase` / `NotificationService` 진입점 spy | `MANDATORY` 트랜잭션 전파 규칙 충돌로 `IllegalTransactionStateException` 발생 | 기각: 하위 아웃바운드 포트/어댑터를 spy해야 함 |
| `NotificationStore` 아웃바운드 포트 빈에 `@MockitoSpyBean` 적용 및 `@BeforeEach` `Mockito.reset` | `insertIfAbsent` 예외 주입 시 Spring `@Transactional` 프록시 작동으로 Postgres 상태·감사 이력·알림 전체 롤백 성공 | 채택: 최종 해결책으로 구성 |

## 6. 최종 해결

- 변경 내용:
  1. `ParticipationPostgreSqlIntegrationTest`에서 `@MockitoSpyBean private NotificationStore notificationStore;`로 스파이 대상을 하위 아웃바운드 포트로 지정하고, `@BeforeEach`에서 `Mockito.reset(notificationStore)` 실행.
  2. `TST-E2-ATOMIC-001`에서 `doThrow(...).when(notificationStore).insertIfAbsent(...)`로 저장 예외를 주입하고 Spring 관리 빈 `adminUseCase.updateSubmission(...)`을 호출하여 PostgreSQL 트랜잭션 롤백(상태 `RECEIVED`, 이력 0건, 알림 0건) 검증.
  3. `docs/troubleshooting/README.md`에서 브랜치에 없는 미추적 파일 링크 정리.
  4. `AdminParticipationService`에서 `toNotificationStatus` 명시적 `switch` 패턴 매핑 적용.
- 선택 이유: Application layer의 `MANDATORY` 프록시는 그대로 유지한 채 아웃바운드 저장소 어댑터 단에서 예외를 주입하여 PostgreSQL 런타임 간의 원자성 롤백을 가장 정합하게 검증하기 위함.
- 변경 파일:
  - [AdminParticipationService.java](../../src/main/java/com/masiton/participation/application/AdminParticipationService.java)
  - [ParticipationPostgreSqlIntegrationTest.java](../../src/test/java/com/masiton/participation/ParticipationPostgreSqlIntegrationTest.java)
  - [README.md](README.md)

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `.\gradlew.bat test --tests "com.masiton.participation.application.**" --tests "com.masiton.participation.presentation.**"` | 통과 | 단위 및 API 테스트 26건 전건 통과 |
| GitHub Actions CI (`백엔드 빌드·테스트`, `프론트엔드 빌드·타입 검사`) | 통과 | Head 커밋 `42a8bbd` CI 파이프라인 검사 전건 통과 완료 |

## 8. 재발 방지 및 다음 확인

- 재발 방지: 트랜잭션 롤백을 검증하는 통합 테스트 작성 시 `new` 수동 객체 생성을 금지하고, 반드시 Spring 컨테이너의 AOP 프록시 빈과 `@MockitoSpyBean` 구체 클래스를 활용하도록 규칙 준수.
- 다음 확인: CI 빌드 전건 통과 완료에 따른 리뷰어 최종 승인 후 `develop` 병합 진행.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| 원자성 롤백 통합 테스트 커버리지 | 미검증 (0건) | `ParticipationPostgreSqlIntegrationTest` 실행 | 통과 (1건) | 알림 저장 실패 시 100% 롤백 보장 확인 | @w00lam (PR #140) |

## 10. 남은 사항

- 없음.
