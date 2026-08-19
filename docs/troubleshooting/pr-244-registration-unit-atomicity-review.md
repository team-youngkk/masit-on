---
related_documents:
  - ../05-specs/api/admin/ai-video-extraction-api.md
  - ../05-specs/data/third-expansion-ai-video-data-contract.md
  - ../06-architecture/transaction-boundaries.md
  - ../07-adr/integration/ai-001-video-extraction-candidate-boundary.md
  - pr-226-ai-auto-registration-contract-review.md
  - pr-175-ai-admin-review-follow-up.md
---

# PR #244 리뷰 트러블슈팅: 등록 단위 실행의 동시성·CONFIRM 원자성과 중복 판정 죽은 코드

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#244 [E3] AI 영상 추출 자동 등록 계약을 구현한다](https://github.com/team-youngkk/masit-on/pull/244) |
| 작성자 | `tjdgns0618` |
| 처리 일자 | 2026-08-19 |
| 범위 | 미해결 인라인 리뷰 3건 (`inan0226` 2건, `jinyp01` 1건) |
| 주 문제 유형 | 애플리케이션 — 동시성 제어와 트랜잭션 경계, 죽은 코드 |
| 기존 기록 | [PR #175 관리자 AI 검수 동시성·태그 감사 후속](pr-175-ai-admin-review-follow-up.md)에서 이미 "검수 상태 전이·태그·감사를 한 트랜잭션으로 묶는다"는 같은 패턴을 다뤘다. 이번 PR의 등록 단위(`ai_registration_unit`) 경로는 그 패턴을 새로 만든 별도 클래스라 같은 결함이 재발했다. [PR #226](pr-226-ai-auto-registration-contract-review.md)은 이 기능의 계약을 확정한 문서 PR로, DB 저장 소스·상태 조합 결정의 배경이다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [등록 단위 잠금 유지 또는 원자화 (P1)](https://github.com/team-youngkk/masit-on/pull/244#discussion_r3810175598) | `FOR UPDATE NOWAIT`가 조회 문장 종료와 함께 풀려 외부 호출 뒤 동시 요청 원자성이 보장되지 않음 | 애플리케이션 | 수정 필요 | `markRegistered`·`confirmWithSupplement`를 `WHERE review_status = expectedReviewStatus` 조건부 갱신으로 바꿔 최종 상태 전이 자체를 원자화. 실제 동시 요청(첫 번째 성공 뒤 두 번째가 같은 `expectedReviewStatus`로 시도) 통합 테스트 추가 | `JdbcAiRegistrationUnitStorePostgreSqlIntegrationTest#markRegistered_동시요청으로상태가바뀐뒤_false를반환하고갱신하지않는다` |
| [CONFIRM 등록·태그·감사 원자 경계 (P1)](https://github.com/team-youngkk/masit-on/pull/244#discussion_r3810175605) | `confirmWithSupplement` 커밋 뒤 태그 연결·감사 insert가 별도 호출이라 실패 시 등록만 남고 감사·재시도 경로가 사라짐 | 애플리케이션 | 수정 필요 | 등록 단위 상태 전이·태그 연결·감사 이력 삽입을 `RegistrationUnitConfirmCommitService`의 단일 `@Transactional` 메서드로 묶음. 감사 insert만 실패시켜 상태 전이까지 롤백되는지 검증하는 회귀 테스트 추가 | `RegistrationUnitConfirmCommitServicePostgreSqlIntegrationTest#commit_감사이력삽입실패_등록단위상태전이도롤백된다` |
| [isDuplicate()의 visitExists 분기 (P3)](https://github.com/team-youngkk/masit-on/pull/244#discussion_r3810199963) | `restaurantExists`가 false일 때만 `visitExists`를 호출하는데 그 쿼리는 restaurant 존재를 전제해 항상 false | 애플리케이션 | 수정 필요 | 데이터 계약상 맛집은 재사용 대상이 아니며(같은 `kakaoPlaceId` 존재 시 유튜버·영상 조합과 무관하게 항상 `DUPLICATE_CONFLICT`) 죽은 분기이므로 삭제. `DuplicateRegistrationCheckPort.visitExists`와 어댑터 구현도 유일한 호출부가 사라져 함께 제거. 이 서비스에 테스트가 전혀 없어 5단계 판정 전체에 대한 새 테스트 클래스 작성 | `RegistrationUnitExecutionServiceTest`(8건, `DUPLICATE_CONFLICT` 포함) |

## 3. 문제 현상과 발생 조건

- 오류 메시지: 없음(리뷰 시점 재현 코드, 런타임 예외 아님)
- 발생 환경: `RegistrationUnitCommandService`(API 3.5·3.6절), `RegistrationUnitExecutionService`(등록 단위 5단계 판정)
- 재현 조건(동시성): 같은 등록 단위에 대해 관리자 두 요청이 근접 시각에 `registerUnit`/`review CONFIRM`을 호출. `lockByJobAndUnitId`의 `FOR UPDATE NOWAIT`는 그 SELECT 문장이 끝나면 즉시 풀리므로(별도 트랜잭션으로 감싸지 않음), 두 요청 모두 `AUTO_BLOCKED`를 읽고 각자 Kakao·YouTube 외부 호출과 `AutoRegisterVerifiedContentUseCase.register()`를 실행할 수 있었다.
- 재현 조건(CONFIRM 원자성): `confirmWithSupplement` 커밋 이후 `connectConfirmedTags`/`appendTagOverrides` 또는 감사 이력 insert 중 하나라도 실패.
- 재현 조건(죽은 코드): `restaurantExists(kakaoPlaceId)`가 false인 모든 호출.
- 실제 결과: (동시성) 두 번째 요청도 `markRegistered`/`confirmWithSupplement`가 무조건 UPDATE라 성공해 버려, 등록 결과가 조용히 뒤엎어질 수 있었다(DB unique 제약이 우연히 막는 경우에만 409). (CONFIRM 원자성) 등록 단위가 `MANUAL_OVERRIDE` + 등록 결과 4종을 가진 채 남고 태그·감사 insert는 누락, `blockReason`이 이미 지워져 있어 재시도 경로도 없다. (죽은 코드) `visitExists` 호출은 이미 restaurant가 없다고 확인된 상태에서 그 restaurant가 있어야만 매칭되는 JOIN을 실행하므로 항상 false.
- 기대 결과: 같은 등록 단위 동시 요청은 하나만 반영되고 나머지는 `409 AIEXTRACT_CONCURRENT_REQUEST_CONFLICT`([API 계약](../05-specs/api/admin/ai-video-extraction-api.md) 452행). CONFIRM은 등록·태그·감사가 모두 반영되거나 모두 반영되지 않아야 한다. 같은 `kakaoPlaceId` 맛집이 이미 있으면 유튜버·영상 조합과 무관하게 항상 `DUPLICATE_CONFLICT`([데이터 계약](../05-specs/data/third-expansion-ai-video-data-contract.md) 191행: "맛집과 방문 관계는 같은 것이 이미 있으면 DUPLICATE_CONFLICT로 차단되어 등록 자체가 일어나지 않으므로 재사용 대상이 될 수 없다").
- 영향 범위: 관리자 등록 단위 일괄 등록(API 3.6절)·`review`의 `CONFIRM`(API 3.5절). 아직 병합 전 PR이라 운영 영향은 없음.

## 4. 근본 원인

- 동시성: `AiRegistrationUnitStore.markRegistered`/`confirmWithSupplement`가 `WHERE id = ?`만으로 무조건 갱신해, `lockByJobAndUnitId`의 짧은 잠금이 풀린 뒤에는 상태 전이 자체를 막을 장치가 없었다. 외부 호출 중 DB 트랜잭션을 열지 않는다는 아키텍처 제약(트랜잭션 경계 문서) 때문에 잠금을 계속 들고 있을 수 없으므로, 최종 갱신에 조건을 거는 낙관적 동시성 제어가 필요했다.
- CONFIRM 원자성: `confirmWithSupplement`, `connectConfirmedTags`/`appendTagOverrides`, `registrationUnitReviewStore.insert`가 각각 독립된 JDBC 호출로 실행되어 트랜잭션 경계가 없었다. `AiExtractionResultCommitService`(Worker 경로)는 이미 같은 유형의 쓰기를 하나의 `@Transactional` 메서드로 묶는 관례를 따르고 있었으나, 이 PR이 새로 만든 관리자 경로에는 그 관례가 적용되지 않았다.
- 죽은 코드: `isDuplicate()` 작성 당시 "같은 맛집이면서 같은 방문 조합"을 노려 두 조건을 순차 확인하려 했으나, `visitExists`의 SQL이 이미 `restaurantExists`가 보장하는 조건(그 kakaoPlaceId의 restaurant 행 존재)을 전제로 JOIN하기 때문에, `restaurantExists`가 false로 걸러진 뒤에는 `visitExists`가 항상 false를 반환한다. `DuplicateRegistrationCheckPort.visitExists`의 자체 Javadoc에도 "세 외부 식별자 중 어느 하나라도 아직 정식 등록되지 않았으면 그 조합의 방문 관계도 존재할 수 없으므로 false"라고 이미 명시돼 있어, 작성자도 이 조건을 인지한 채로 죽은 분기를 남긴 것으로 보인다(추정).

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| `AiExtractionResultCommitService`/`AiExtractionResultCommitServicePostgreSqlIntegrationTest` 관례 확인 | 같은 "외부 호출은 호출자가 끝낸 뒤 순수 DB 쓰기만 한 트랜잭션으로 묶는다" 패턴이 이미 Worker 경로에 있음 | CONFIRM 경로에도 같은 패턴(`RegistrationUnitConfirmCommitService`)을 그대로 적용 |
| `DuplicateRegistrationCheckPort`/`DuplicateRegistrationCheckQueryAdapter`, 관련 테스트 검색 | `visitExists`를 호출하거나 검증하는 테스트가 전혀 없었음 | 죽은 분기 삭제와 함께 포트 메서드·어댑터 구현도 완전히 제거(사용처 없는 코드 유지 금지) |
| `RegistrationUnitExecutionService`/`RegistrationUnitCommandService`를 직접 검증하는 테스트 검색 | 두 클래스 모두 직접 단위 테스트가 없었고(전체 흐름은 `RegistrationUnitAutoExecutionServiceTest`가 `ExecuteRegistrationUnitUseCase`를 mock으로 대체해 우회), `RegistrationUnitAutoExecutionServiceTest`의 Javadoc은 "5단계 판정 세부 규칙은 `RegistrationUnitExecutionServiceTest`가 검증한다"고 이미 언급하고 있었음(실제로는 존재하지 않던 파일) | 리뷰가 지적한 테스트 공백을 이번에 채움: `RegistrationUnitExecutionServiceTest`(8건), `RegistrationUnitCommandServiceTest`(4건) 신규 작성 |
| 실제 Testcontainers PostgreSQL로 CAS(조건부 갱신) 경합 재현 | 같은 `unitId`에 대해 `markRegistered(unitId, "AUTO_BLOCKED", ...)`를 두 번 연속 호출하면 두 번째가 `false`를 반환하고 첫 번째 결과가 유지됨을 확인 | 스레드/실행 순서 의존 없이 결정적으로 재현 가능(ADR-TEST-001의 `Thread.sleep()` 금지 원칙에 맞음) |

## 6. 최종 해결

- 변경 내용:
  - `AiRegistrationUnitStore.markRegistered`/`confirmWithSupplement`가 `expectedReviewStatus`를 받아 `WHERE id = ? AND review_status = ?`로 조건부 갱신하고 갱신 여부를 `boolean`으로 반환하도록 변경(`JdbcAiRegistrationUnitStore` 구현 포함).
  - `RegistrationUnitCommandService`가 조건부 갱신 실패(또는 `DataIntegrityViolationException`)를 모두 `concurrentConflict()` 헬퍼로 통일해 `409 AIEXTRACT_CONCURRENT_REQUEST_CONFLICT`로 응답.
  - CONFIRM의 등록 단위 상태 전이·태그 연결·감사 이력 삽입을 새 `RegistrationUnitConfirmCommitService`(`@Transactional`)로 이동해 하나의 트랜잭션으로 커밋.
  - `RegistrationUnitExecutionService.isDuplicate()`를 `restaurantExists`만 확인하도록 단순화하고, `DuplicateRegistrationCheckPort.visitExists`와 그 어댑터 구현을 삭제.
- 선택 이유: 외부 호출 중 DB 트랜잭션을 열지 않는다는 기존 아키텍처 제약을 지키면서 동시성을 막으려면, 잠금을 계속 들고 있는 대신 최종 쓰기 시점에 상태를 재확인하는 조건부 갱신(낙관적 동시성 제어)이 최소 변경이었다. CONFIRM 원자성은 이미 저장소에 있는 `AiExtractionResultCommitService` 관례를 그대로 재사용해 새 패턴을 만들지 않았다.
- 변경 파일:
  - `src/main/java/com/masiton/ai/application/port/out/AiRegistrationUnitStore.java`
  - `src/main/java/com/masiton/ai/infrastructure/persistence/JdbcAiRegistrationUnitStore.java`
  - `src/main/java/com/masiton/ai/application/RegistrationUnitCommandService.java`
  - `src/main/java/com/masiton/ai/application/RegistrationUnitConfirmCommitService.java`(신규)
  - `src/main/java/com/masiton/orchestration/application/RegistrationUnitExecutionService.java`
  - `src/main/java/com/masiton/orchestration/application/port/out/DuplicateRegistrationCheckPort.java`
  - `src/main/java/com/masiton/orchestration/infrastructure/query/DuplicateRegistrationCheckQueryAdapter.java`
- 고려한 대안: (1) `lockByJobAndUnitId` 자체를 메서드 전체를 감싸는 트랜잭션 안으로 옮겨 외부 호출이 끝날 때까지 잠금 유지 — 외부 호출 중 DB 트랜잭션을 열지 않는다는 기존 아키텍처 규칙과 정면으로 충돌해 채택하지 않음. (2) `visitExists` 분기를 죽이지 않고 의도를 주석으로만 남김 — 포트 Javadoc이 이미 그 죽은 조건을 설명하고 있어 방어 코드로 볼 근거가 없었고, 유일한 호출부가 사라지면 포트 자체가 미사용 코드가 되므로 완전 삭제를 선택.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `./gradlew compileJava compileTestJava` | 통과 | 신규·변경 파일 컴파일 오류 없음 |
| `./gradlew test --tests "com.masiton.orchestration.application.RegistrationUnitExecutionServiceTest"` | 통과 | 8건(정상 확정, 7종 차단 사유 중 5종 대표, `DUPLICATE_CONFLICT` 포함) |
| `./gradlew test --tests "com.masiton.ai.application.RegistrationUnitCommandServiceTest"` | 통과 | 4건(등록 단위 일괄 등록 성공·CAS 충돌·unique 제약 충돌, review CONFIRM CAS 충돌 시 감사 이력 미삽입) |
| `./gradlew test --tests "com.masiton.ai.infrastructure.persistence.JdbcAiRegistrationUnitStorePostgreSqlIntegrationTest"` | 통과 | 13건(기존 9건 + CAS 신규 4건: markRegistered 없음/충돌, confirmWithSupplement 없음/충돌) |
| `./gradlew test --tests "com.masiton.ai.application.RegistrationUnitConfirmCommitServicePostgreSqlIntegrationTest"` | 통과 | 3건(감사 이력 삽입 실패 시 전체 롤백, expectedReviewStatus 불일치 시 무변경, 정상 커밋) |
| `./gradlew test --tests "com.masiton.ai.*" --tests "com.masiton.orchestration.*"` | 통과 | 412건, 실패·오류 0건(회귀 없음) |
| `./gradlew clean build`(전체 백엔드 빌드) | 통과 | 프론트엔드 변경 없음 |

## 8. 재발 방지 및 다음 확인

- 재발 방지: 외부 호출 뒤 최종 DB 반영이 있는 새 Application 서비스를 작성할 때는 (1) 상태 전이가 있으면 `expectedStatus` 조건부 갱신, (2) 여러 쓰기가 있으면 `AiExtractionResultCommitService`/`RegistrationUnitConfirmCommitService`처럼 순수 DB 쓰기만 모은 `@Transactional` 커밋 서비스로 분리하는 두 관례를 [PR #175 기록](pr-175-ai-admin-review-follow-up.md)에 이어 이 기록에도 남긴다.
- 다음 확인: 없음. PR #244 본문의 "검증하지 못한 항목"에 남아 있던 `DISCARD`/`ROLLBACK`/`ADJUST_CATEGORY`의 동시 요청 원자성은 이번 스레드의 요청 범위 밖이며 PR 본문이 이미 후속 작업으로 분리하겠다고 명시했으므로 이 기록에서 추가 처리하지 않는다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| 등록 단위 동시 요청 충돌 오탐지율 | 해당 없음 — 병합 전 PR이라 운영 트래픽 기준값이 없음 | - | - | 해당 없음 | 담당자 `tjdgns0618`, M2 운영 배포 뒤 `AIEXTRACT_CONCURRENT_REQUEST_CONFLICT` 발생 빈도를 로그로 1회 확인 예정(추적 이슈 없음, 필요 시 신규 이슈로 등록) |

## 10. 남은 사항

없음. 3개 스레드 모두 수정 완료 후 답글·해결 처리한다.
