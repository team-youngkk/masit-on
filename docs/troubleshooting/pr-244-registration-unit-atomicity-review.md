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
| 범위 | 1차: 미해결 인라인 리뷰 3건(`inan0226` 2건, `jinyp01` 1건). 2차: 1차 반영 뒤 새로 열린 인라인 리뷰 9건(`w00lam` 6건 — 그중 2건은 1차와 같은 문제를 겨냥한 별도 스레드, `jinyp01` 1건, `inan0226` 2건) |
| 주 문제 유형 | 애플리케이션 — 동시성 제어와 트랜잭션 경계, 죽은 코드, 식별자 변환 |
| 기존 기록 | [PR #175 관리자 AI 검수 동시성·태그 감사 후속](pr-175-ai-admin-review-follow-up.md)에서 이미 "검수 상태 전이·태그·감사를 한 트랜잭션으로 묶는다"는 같은 패턴을 다뤘다. 이번 PR의 등록 단위(`ai_registration_unit`) 경로는 그 패턴을 새로 만든 별도 클래스라 같은 결함이 재발했다. [PR #226](pr-226-ai-auto-registration-contract-review.md)은 이 기능의 계약을 확정한 문서 PR로, DB 저장 소스·상태 조합 결정의 배경이다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [등록 단위 잠금 유지 또는 원자화 (P1)](https://github.com/team-youngkk/masit-on/pull/244#discussion_r3810175598) | `FOR UPDATE NOWAIT`가 조회 문장 종료와 함께 풀려 외부 호출 뒤 동시 요청 원자성이 보장되지 않음 | 애플리케이션 | 수정 필요 | `markRegistered`·`confirmWithSupplement`를 `WHERE review_status = expectedReviewStatus` 조건부 갱신으로 바꿔 최종 상태 전이 자체를 원자화. 실제 동시 요청(첫 번째 성공 뒤 두 번째가 같은 `expectedReviewStatus`로 시도) 통합 테스트 추가 | `JdbcAiRegistrationUnitStorePostgreSqlIntegrationTest#markRegistered_동시요청으로상태가바뀐뒤_false를반환하고갱신하지않는다` |
| [CONFIRM 등록·태그·감사 원자 경계 (P1)](https://github.com/team-youngkk/masit-on/pull/244#discussion_r3810175605) | `confirmWithSupplement` 커밋 뒤 태그 연결·감사 insert가 별도 호출이라 실패 시 등록만 남고 감사·재시도 경로가 사라짐 | 애플리케이션 | 수정 필요 | 등록 단위 상태 전이·태그 연결·감사 이력 삽입을 `RegistrationUnitConfirmCommitService`의 단일 `@Transactional` 메서드로 묶음. 감사 insert만 실패시켜 상태 전이까지 롤백되는지 검증하는 회귀 테스트 추가 | `RegistrationUnitConfirmCommitServicePostgreSqlIntegrationTest#commit_감사이력삽입실패_등록단위상태전이도롤백된다` |
| [isDuplicate()의 visitExists 분기 (P3)](https://github.com/team-youngkk/masit-on/pull/244#discussion_r3810199963) | `restaurantExists`가 false일 때만 `visitExists`를 호출하는데 그 쿼리는 restaurant 존재를 전제해 항상 false | 애플리케이션 | 수정 필요 | 데이터 계약상 맛집은 재사용 대상이 아니며(같은 `kakaoPlaceId` 존재 시 유튜버·영상 조합과 무관하게 항상 `DUPLICATE_CONFLICT`) 죽은 분기이므로 삭제. `DuplicateRegistrationCheckPort.visitExists`와 어댑터 구현도 유일한 호출부가 사라져 함께 제거. 이 서비스에 테스트가 전혀 없어 5단계 판정 전체에 대한 새 테스트 클래스 작성 | `RegistrationUnitExecutionServiceTest`(8건, `DUPLICATE_CONFLICT` 포함) |

### 2차 리뷰 라운드 (1차 반영 뒤 새로 열린 스레드)

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [등록 단위 잠금 결함 (P1, w00lam)](https://github.com/team-youngkk/masit-on/pull/244#discussion_r3810187036) | 위 1차 스레드와 같은 `FOR UPDATE NOWAIT` 원자성 결함을 별도 스레드로 재보고 | 애플리케이션 | 이미 해결 | 1차에서 이미 CAS 조건부 갱신으로 반영됨 | 1차 처리 결과와 동일 커밋 참조 |
| [CONFIRM 원자 경계 결함 (P1, w00lam)](https://github.com/team-youngkk/masit-on/pull/244#discussion_r3810187312) | 위 1차 스레드와 같은 CONFIRM 원자성 결함을 별도 스레드로 재보고 | 애플리케이션 | 이미 해결 | 1차에서 이미 `RegistrationUnitConfirmCommitService`로 반영됨 | 1차 처리 결과와 동일 커밋 참조 |
| [adminId 이중 변환 (P1, w00lam)](https://github.com/team-youngkk/masit-on/pull/244#discussion_r3810187649) | Controller가 이미 `admin_account.id`로 변환한 값을 서비스가 다시 `member_account.id`로 취급해 재변환·저장 | 애플리케이션 | 수정 필요 | `AdminAiVideoExtractionController.adminId()`에서 `LegacyAdminActorResolver.resolve()` 호출 제거, JWT principal(`member_account.id`)을 그대로 전달 | `AdminAiVideoExtractionControllerApiTest#review_principal의memberAccountId를변환없이그대로전달한다` |
| [깨진 Javadoc 인코딩 (P2, w00lam)](https://github.com/team-youngkk/masit-on/pull/244#discussion_r3810187894) | `review()` Javadoc이 UTF-8 인코딩 깨짐(`?`)으로 보임 | 애플리케이션 | 수정 불필요 | 현재 HEAD 파일은 `file -i`로 `charset=utf-8` 확인, Read 도구로도 정상 한글 렌더링됨. 실제 저장소 파일은 손상되지 않았고 git 이력에도 손상된 커밋이 없음(리뷰 도구의 인코딩 표시 문제로 추정) | `file -i src/main/java/com/masiton/ai/application/RegistrationUnitCommandService.java` → `charset=utf-8` |
| [V6→V8 주석 오기 (P3, w00lam)](https://github.com/team-youngkk/masit-on/pull/244#discussion_r3810188148) | 테스트 Javadoc이 재번호 전 `V6`을 그대로 참조 | 애플리케이션 | 수정 필요 | 주석을 `V8`로 정정 | 코드 리뷰(단순 문자열 정정, 별도 테스트 불필요) |
| [rollback·discard·adjustCategory CAS 미적용 (P2, jinyp01)](https://github.com/team-youngkk/masit-on/pull/244#discussion_r3810389409) | 1차에서 `markRegistered`/`confirmWithSupplement`만 CAS로 바뀌고 나머지 세 결정은 그대로 무조건 갱신 | 애플리케이션 | 수정 필요 | `AiRegistrationUnitStore.rollback`/`discard`/`adjustCategory`에도 `expectedReviewStatus` 조건부 갱신 적용(`rollback`·`adjustCategory`는 `registered_restaurant_id IS NOT NULL`도 추가 조건) | `JdbcAiRegistrationUnitStorePostgreSqlIntegrationTest`·`RegistrationUnitCommandServiceTest`의 CAS 불일치 테스트 |
| [모든 review 결정의 동시성 계약 보장 (P1, inan0226)](https://github.com/team-youngkk/masit-on/pull/244#discussion_r3810394516) | `DISCARD`·`ROLLBACK`·`ADJUST_CATEGORY`도 동시 요청 시 API 계약의 `409`를 보장해야 함 | 애플리케이션 | 수정 필요 | 위 jinyp01 스레드와 같은 수정으로 해소 | 동일 |
| [정식 등록·CONFIRM 커밋 원자 경계 (P1, inan0226)](https://github.com/team-youngkk/masit-on/pull/244#discussion_r3810394514) | `AutoRegisterVerifiedContentService.register()`가 별도 트랜잭션에서 먼저 커밋되고, 등록 단위 상태·태그·감사는 나중에 별도 트랜잭션으로 반영되어 후자가 실패하면 4종 정식 데이터가 고아로 남음 | 애플리케이션 | 수정 필요(제안한 대안 채택) | 두 트랜잭션을 하나로 합치는 대신, 리뷰가 제시한 대안인 "commit 실패 시 보상 롤백"을 채택: `markRegistered`/`confirmCommitService.commit()`이 CAS 실패 또는 unique 제약 위반으로 실패하면 `RollbackAiRegisteredContentUseCase.rollback()`으로 방금 만든 4종을 즉시 되돌려 재시도 가능한 상태로 복구 | `RegistrationUnitCommandServiceTest`의 보상 롤백 검증(마이크로 단위), 근본적인 단일 트랜잭션 병합은 Worker 경로도 공유하는 `ExecuteRegistrationUnitUseCase` 계약 변경이 필요해 이번 PR 범위에서는 보상 롤백으로 대체 |
| [adminId FK 불일치 (P1, w00lam)](https://github.com/team-youngkk/masit-on/pull/244#discussion_r3810399031) | `RegistrationUnitConfirmCommitService.commit()`이 이미 `admin_account.id`로 변환된 값을 `member_account.id`로 취급 | 애플리케이션 | 수정 필요 | 위 adminId 이중 변환 스레드와 같은 원인·같은 수정(Controller에서 변환 제거)으로 해소 | 동일 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: 없음(리뷰 시점 재현 코드, 런타임 예외 아님)
- 발생 환경: `RegistrationUnitCommandService`(API 3.5·3.6절), `RegistrationUnitExecutionService`(등록 단위 5단계 판정)
- 재현 조건(동시성): 같은 등록 단위에 대해 관리자 두 요청이 근접 시각에 `registerUnit`/`review CONFIRM`을 호출. `lockByJobAndUnitId`의 `FOR UPDATE NOWAIT`는 그 SELECT 문장이 끝나면 즉시 풀리므로(별도 트랜잭션으로 감싸지 않음), 두 요청 모두 `AUTO_BLOCKED`를 읽고 각자 Kakao·YouTube 외부 호출과 `AutoRegisterVerifiedContentUseCase.register()`를 실행할 수 있었다.
- 재현 조건(CONFIRM 원자성): `confirmWithSupplement` 커밋 이후 `connectConfirmedTags`/`appendTagOverrides` 또는 감사 이력 insert 중 하나라도 실패.
- 재현 조건(죽은 코드): `restaurantExists(kakaoPlaceId)`가 false인 모든 호출.
- 실제 결과: (동시성) 두 번째 요청도 `markRegistered`/`confirmWithSupplement`가 무조건 UPDATE라 성공해 버려, 등록 결과가 조용히 뒤엎어질 수 있었다(DB unique 제약이 우연히 막는 경우에만 409). (CONFIRM 원자성) 등록 단위가 `MANUAL_OVERRIDE` + 등록 결과 4종을 가진 채 남고 태그·감사 insert는 누락, `blockReason`이 이미 지워져 있어 재시도 경로도 없다. (죽은 코드) `visitExists` 호출은 이미 restaurant가 없다고 확인된 상태에서 그 restaurant가 있어야만 매칭되는 JOIN을 실행하므로 항상 false.
- 기대 결과: 같은 등록 단위 동시 요청은 하나만 반영되고 나머지는 `409 AIEXTRACT_CONCURRENT_REQUEST_CONFLICT`([API 계약](../05-specs/api/admin/ai-video-extraction-api.md) 452행). CONFIRM은 등록·태그·감사가 모두 반영되거나 모두 반영되지 않아야 한다. 같은 `kakaoPlaceId` 맛집이 이미 있으면 유튜버·영상 조합과 무관하게 항상 `DUPLICATE_CONFLICT`([데이터 계약](../05-specs/data/third-expansion-ai-video-data-contract.md) 191행: "맛집과 방문 관계는 같은 것이 이미 있으면 DUPLICATE_CONFLICT로 차단되어 등록 자체가 일어나지 않으므로 재사용 대상이 될 수 없다").
- 영향 범위: 관리자 등록 단위 일괄 등록(API 3.6절)·`review`의 `CONFIRM`(API 3.5절). 아직 병합 전 PR이라 운영 영향은 없음.

### 2차 리뷰 라운드 추가 현상

- 재현 조건(adminId): `AdminAiVideoExtractionController.adminId()`가 `legacyAdminActorResolver.resolve(memberAccountId)`로 `admin_account.id`를 먼저 만든 뒤 `review()`에 넘기고, `RegistrationUnitConfirmCommitService.commit()`이 이 값을 다시 `member_account.id`로 취급해 `legacyAdminActorResolver.resolve()`를 한 번 더 호출하고 `ai_registration_unit_review.reviewed_by`(FK `member_account(id)`)에도 그대로 저장.
- 실제 결과(adminId): `appendTagOverrides` 경로에서는 이미 변환된 `admin_account.id`를 다시 `member_account_id`로 조회하려다 `403 FORBIDDEN`(`JdbcLegacyAdminActorResolver`가 매핑을 못 찾으면 예외), `ai_registration_unit_review` insert 경로에서는 FK 제약 위반으로 `CONFIRM` 자체가 실패할 수 있었다.
- 재현 조건(register+CONFIRM 경계): CONFIRM에서 `executeRegistrationUnit.execute()`가 내부적으로 `autoRegister.register()`를 호출해 그 자리에서 즉시 커밋한 뒤, `RegistrationUnitConfirmCommitService.commit()`이 별도 트랜잭션으로 등록 단위 상태·태그·감사를 반영하다가 CAS 충돌 또는 unique 제약 위반으로 실패.
- 실제 결과(register+CONFIRM 경계): 방금 만든 restaurant·visit(및 필요 시 creator·video)가 어떤 등록 단위에도 연결되지 못한 채 DB에 남고, 등록 단위는 여전히 `AUTO_BLOCKED`라 재시도할 수 있지만 재시도 시 `isDuplicate()`가 방금 만든 restaurant를 발견해 `DUPLICATE_CONFLICT`로 영구히 막힌다.

## 4. 근본 원인

- 동시성: `AiRegistrationUnitStore.markRegistered`/`confirmWithSupplement`가 `WHERE id = ?`만으로 무조건 갱신해, `lockByJobAndUnitId`의 짧은 잠금이 풀린 뒤에는 상태 전이 자체를 막을 장치가 없었다. 외부 호출 중 DB 트랜잭션을 열지 않는다는 아키텍처 제약(트랜잭션 경계 문서) 때문에 잠금을 계속 들고 있을 수 없으므로, 최종 갱신에 조건을 거는 낙관적 동시성 제어가 필요했다.
- CONFIRM 원자성: `confirmWithSupplement`, `connectConfirmedTags`/`appendTagOverrides`, `registrationUnitReviewStore.insert`가 각각 독립된 JDBC 호출로 실행되어 트랜잭션 경계가 없었다. `AiExtractionResultCommitService`(Worker 경로)는 이미 같은 유형의 쓰기를 하나의 `@Transactional` 메서드로 묶는 관례를 따르고 있었으나, 이 PR이 새로 만든 관리자 경로에는 그 관례가 적용되지 않았다.
- 죽은 코드: `isDuplicate()` 작성 당시 "같은 맛집이면서 같은 방문 조합"을 노려 두 조건을 순차 확인하려 했으나, `visitExists`의 SQL이 이미 `restaurantExists`가 보장하는 조건(그 kakaoPlaceId의 restaurant 행 존재)을 전제로 JOIN하기 때문에, `restaurantExists`가 false로 걸러진 뒤에는 `visitExists`가 항상 false를 반환한다. `DuplicateRegistrationCheckPort.visitExists`의 자체 Javadoc에도 "세 외부 식별자 중 어느 하나라도 아직 정식 등록되지 않았으면 그 조합의 방문 관계도 존재할 수 없으므로 false"라고 이미 명시돼 있어, 작성자도 이 조건을 인지한 채로 죽은 분기를 남긴 것으로 보인다(추정).
- adminId 이중 변환: Controller의 `adminId()`가 애초에 legacy `admin_account` FK를 위해 변환된 값을 반환하도록 작성돼 있었는데, `RegistrationUnitCommandService`(이번 PR이 새로 만든 등록 단위 경로)는 반대로 "raw member_account id가 넘어온다"고 가정하고 짠 코드였다. 두 가정이 서로 어긋난 채로 함께 존재했고, Controller의 유일한 호출부(`review()`)가 이 어긋남을 가려 로컬 개발에서는 드러나지 않았다(추정 — 실제 실행 시 어느 경로가 먼저 예외를 던지는지는 미검증).
- register+CONFIRM 원자 경계: `ExecuteRegistrationUnitUseCase.execute()`는 Kakao·YouTube 외부 호출이 끝난 뒤 `autoRegister.register()`를 내부에서 직접 호출해 그 자체 트랜잭션으로 즉시 커밋한다(Worker 경로의 `RegistrationUnitAutoExecutionService`도 동일하게 이 계약을 통해 호출하므로 같은 특성을 공유한다). `RegistrationUnitCommandService`는 이 결과를 받은 뒤 별도 트랜잭션(`RegistrationUnitConfirmCommitService`)에서 등록 단위 상태를 반영하므로, 두 트랜잭션 사이에 원자성이 없다. 완전한 해결은 `execute()`가 "검증"과 "등록"을 분리해 등록을 호출자의 트랜잭션 안에서 수행하도록 계약을 바꿔야 하는데, 이는 Worker·관리자 두 경로가 공유하는 계약이라 이번 PR의 범위를 벗어난다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| `AiExtractionResultCommitService`/`AiExtractionResultCommitServicePostgreSqlIntegrationTest` 관례 확인 | 같은 "외부 호출은 호출자가 끝낸 뒤 순수 DB 쓰기만 한 트랜잭션으로 묶는다" 패턴이 이미 Worker 경로에 있음 | CONFIRM 경로에도 같은 패턴(`RegistrationUnitConfirmCommitService`)을 그대로 적용 |
| `DuplicateRegistrationCheckPort`/`DuplicateRegistrationCheckQueryAdapter`, 관련 테스트 검색 | `visitExists`를 호출하거나 검증하는 테스트가 전혀 없었음 | 죽은 분기 삭제와 함께 포트 메서드·어댑터 구현도 완전히 제거(사용처 없는 코드 유지 금지) |
| `RegistrationUnitExecutionService`/`RegistrationUnitCommandService`를 직접 검증하는 테스트 검색 | 두 클래스 모두 직접 단위 테스트가 없었고(전체 흐름은 `RegistrationUnitAutoExecutionServiceTest`가 `ExecuteRegistrationUnitUseCase`를 mock으로 대체해 우회), `RegistrationUnitAutoExecutionServiceTest`의 Javadoc은 "5단계 판정 세부 규칙은 `RegistrationUnitExecutionServiceTest`가 검증한다"고 이미 언급하고 있었음(실제로는 존재하지 않던 파일) | 리뷰가 지적한 테스트 공백을 이번에 채움: `RegistrationUnitExecutionServiceTest`(8건), `RegistrationUnitCommandServiceTest`(4건) 신규 작성 |
| 실제 Testcontainers PostgreSQL로 CAS(조건부 갱신) 경합 재현 | 같은 `unitId`에 대해 `markRegistered(unitId, "AUTO_BLOCKED", ...)`를 두 번 연속 호출하면 두 번째가 `false`를 반환하고 첫 번째 결과가 유지됨을 확인 | 스레드/실행 순서 의존 없이 결정적으로 재현 가능(ADR-TEST-001의 `Thread.sleep()` 금지 원칙에 맞음) |
| `AdminAiVideoExtractionController`에서 `adminId(authentication)`의 유일한 호출부 검색 | `review()` 한 곳뿐이고, `RegistrationUnitConfirmCommitService`·`port.appendTagOverrides` 외에 이 값을 다른 형태로 기대하는 호출부가 없음 | Controller에서 변환을 제거해도 다른 흐름에 영향 없음을 확인하고 안전하게 수정 |
| `file -i`와 Read 도구로 `RegistrationUnitCommandService.java`의 실제 인코딩 확인 | `charset=utf-8`이고 Read 도구로도 정상 한글 렌더링됨. `git log -p`로 이 Javadoc 블록의 전체 이력을 확인해도 손상된 커밋이 없음 | 실제 파일은 손상되지 않았다고 결론 — 리뷰 코멘트의 `?` 문자는 리뷰 도구 쪽 인코딩 표시 문제로 추정, 수정 불필요로 판단 |
| Worker 경로(`RegistrationUnitAutoExecutionService`)도 `ExecuteRegistrationUnitUseCase.execute()`를 거치는지, `autoRegister.register()` 호출 위치가 어디인지 확인 | Worker 경로도 관리자 경로와 동일하게 `execute()` 내부에서 `register()`가 즉시 커밋되는 구조를 공유함(`RegistrationUnitAutoExecutionService`도 트랜잭션 밖에서 실행하도록 Javadoc에 명시) | register+최종 커밋의 완전한 단일 트랜잭션 병합은 두 경로가 공유하는 계약 변경이 필요해 이번 PR 범위를 벗어난다고 판단, 대신 보상 롤백을 채택 |

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

### 2차 리뷰 라운드 최종 해결

- 변경 내용:
  - `AdminAiVideoExtractionController.adminId()`에서 `LegacyAdminActorResolver.resolve()` 호출 제거. 이제 JWT principal(`member_account.id`)을 그대로 반환하며, 그 결과로 `LegacyAdminActorResolver` 필드·생성자 인자가 미사용이 되어 함께 제거.
  - `AiRegistrationUnitStore.rollback`/`discard`/`adjustCategory`에 `expectedReviewStatus` 파라미터를 추가해 `markRegistered`/`confirmWithSupplement`와 같은 조건부 갱신으로 통일(`rollback`·`adjustCategory`는 `registered_restaurant_id IS NOT NULL` 조건도 추가 — `review_status`만으로는 `MANUAL_OVERRIDE`가 "여전히 등록됨"과 "이미 롤백·폐기됨"을 구분하지 못하기 때문).
  - `RegistrationUnitCommandService`에 `compensateFailedRegistration()`을 추가해, `markRegistered`/`confirmCommitService.commit()`이 CAS 실패 또는 `DataIntegrityViolationException`으로 실패하면 방금 만든 등록 콘텐츠를 `RollbackAiRegisteredContentUseCase.rollback()`으로 즉시 되돌린다.
  - `src/test/java/com/masiton/restaurant/infrastructure/persistence/FoodCategoryMappingRepositoryPortIntegrationTest.java`의 Javadoc `V6` → `V8` 정정.
- 선택 이유: adminId는 두 지점(Controller·Service) 중 하나만 변환해야 하는데, Service 쪽 코드(`ai_registration_unit_review` FK, `appendTagOverrides`의 legacy 변환)가 원본 PR #226 계약과 다른 도메인의 기존 관례(`ai_candidate_tag_review`도 같은 패턴)를 그대로 따르고 있어 더 많은 곳이 의존하므로, Controller 쪽의 사전 변환을 제거하는 편이 변경 범위가 더 작았다. register+CONFIRM 원자 경계는 inan0226이 직접 제시한 두 대안(단일 트랜잭션 재구성 vs. 보상 롤백) 중 후자를 선택했다 — 전자는 Worker 경로도 공유하는 `ExecuteRegistrationUnitUseCase` 계약을 바꿔야 해서 소유자 합의 없이 단독으로 진행하기에는 범위가 컸다.
- 변경 파일(2차):
  - `src/main/java/com/masiton/ai/presentation/AdminAiVideoExtractionController.java`
  - `src/main/java/com/masiton/ai/application/port/out/AiRegistrationUnitStore.java`
  - `src/main/java/com/masiton/ai/infrastructure/persistence/JdbcAiRegistrationUnitStore.java`
  - `src/main/java/com/masiton/ai/application/RegistrationUnitCommandService.java`
  - `src/test/java/com/masiton/restaurant/infrastructure/persistence/FoodCategoryMappingRepositoryPortIntegrationTest.java`
- 고려한 대안: register+CONFIRM 원자 경계에 대해 `execute()`가 검증과 등록을 분리해 등록 자체를 호출자 트랜잭션 안에서 수행하도록 계약을 재설계하는 방안을 검토했으나, Worker 경로(`RegistrationUnitAutoExecutionService`/`AiExtractionResultCommitService`)도 같은 계약을 쓰고 있어 두 소비자에 영향을 준다. API·계약 변경은 소유자와 사전 합의가 필요하다는 프로젝트 규칙에 따라 이번 PR 단독으로 진행하지 않고, 리뷰가 제시한 보상 롤백으로 대체했다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `./gradlew compileJava compileTestJava` | 통과 | 신규·변경 파일 컴파일 오류 없음 |
| `./gradlew test --tests "com.masiton.orchestration.application.RegistrationUnitExecutionServiceTest"` | 통과 | 8건(정상 확정, 7종 차단 사유 중 5종 대표, `DUPLICATE_CONFLICT` 포함) |
| `./gradlew test --tests "com.masiton.ai.application.RegistrationUnitCommandServiceTest"` | 통과 | 4건(등록 단위 일괄 등록 성공·CAS 충돌·unique 제약 충돌, review CONFIRM CAS 충돌 시 감사 이력 미삽입) |
| `./gradlew test --tests "com.masiton.ai.infrastructure.persistence.JdbcAiRegistrationUnitStorePostgreSqlIntegrationTest"` | 통과 | 13건(기존 9건 + CAS 신규 4건: markRegistered 없음/충돌, confirmWithSupplement 없음/충돌) |
| `./gradlew test --tests "com.masiton.ai.application.RegistrationUnitConfirmCommitServicePostgreSqlIntegrationTest"` | 통과 | 3건(감사 이력 삽입 실패 시 전체 롤백, expectedReviewStatus 불일치 시 무변경, 정상 커밋) |
| `./gradlew test --tests "com.masiton.ai.*" --tests "com.masiton.orchestration.*"` | 통과 | 412건, 실패·오류 0건(회귀 없음) |
| `./gradlew clean build`(전체 백엔드 빌드, 1차) | 통과 | 1301건, 실패·오류 0건 |
| `./gradlew test --tests "...AdminAiVideoExtractionControllerApiTest"` | 통과 | adminId 그대로 전달 회귀 테스트 포함 10건 |
| `./gradlew test --tests "...RegistrationUnitCommandServiceTest"` | 통과 | 보상 롤백·4종 결정 CAS 충돌 포함 7건 |
| `./gradlew test --tests "...JdbcAiRegistrationUnitStorePostgreSqlIntegrationTest"` | 통과 | rollback·discard·adjustCategory CAS 신규 3건 포함 16건 |
| `./gradlew test --tests "...FoodCategoryMappingRepositoryPortIntegrationTest"` | 통과 | V8 주석 정정 후 4건 |
| `./gradlew clean build`(전체 백엔드 빌드, 2차) | 통과 | 1380건, 실패·오류 0건(회귀 없음) |

## 8. 재발 방지 및 다음 확인

- 재발 방지: 외부 호출 뒤 최종 DB 반영이 있는 새 Application 서비스를 작성할 때는 (1) 상태 전이가 있으면 `expectedStatus` 조건부 갱신(등록 결과 존재 여부처럼 상태 문자열만으로 구분 안 되는 불변식이 있으면 추가 WHERE 조건도 함께), (2) 여러 쓰기가 있으면 `AiExtractionResultCommitService`/`RegistrationUnitConfirmCommitService`처럼 순수 DB 쓰기만 모은 `@Transactional` 커밋 서비스로 분리, (3) Controller와 Service 경계를 넘는 식별자(특히 `member_account.id`/`admin_account.id`처럼 의미가 다른 두 UUID)는 어느 계층이 변환을 책임지는지 타입이나 이름만으로는 구분되지 않으므로 값을 넘기기 전에 호출부를 전수 확인하는 세 관례를 [PR #175 기록](pr-175-ai-admin-review-follow-up.md)에 이어 이 기록에도 남긴다.
- 다음 확인: register+CONFIRM(및 등록 단위 일괄 등록)의 완전한 단일 트랜잭션 병합은 Worker·관리자 두 경로가 공유하는 `ExecuteRegistrationUnitUseCase` 계약 변경이 필요하다. 이번 PR은 보상 롤백으로 재시도 가능성만 보장했고, 계약 자체를 바꾸는 근본 해결은 별도 이슈로 등록해 담당자·소유자 합의 후 진행해야 한다(추적 이슈 없음, 필요 시 신규 등록). PR #244 본문의 "검증하지 못한 항목"에 남아 있던 `DISCARD`/`ROLLBACK`/`ADJUST_CATEGORY`가 외부 콘텐츠 반영(`rollbackUseCase.rollback()`/`adjustCategoryUseCase.adjust()`)까지 포함해 완전히 원자적이지는 않다는 잔여 위험도 PR 본문이 이미 후속 작업으로 분리하겠다고 명시한 대로 유지한다(이번 라운드에서는 등록 단위 행 자체의 CAS만 추가했다).

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| 등록 단위 동시 요청 충돌 오탐지율 | 해당 없음 — 병합 전 PR이라 운영 트래픽 기준값이 없음 | - | - | 해당 없음 | 담당자 `tjdgns0618`, M2 운영 배포 뒤 `AIEXTRACT_CONCURRENT_REQUEST_CONFLICT` 발생 빈도를 로그로 1회 확인 예정(추적 이슈 없음, 필요 시 신규 이슈로 등록) |

## 10. 남은 사항

1차·2차 모두 12개 스레드를 수정 완료 후 답글·해결 처리한다. 이 중 register+CONFIRM 원자 경계는 보상 롤백으로 즉시 위험(영구 `DUPLICATE_CONFLICT` 잠금)만 해소했고, `ExecuteRegistrationUnitUseCase` 계약을 바꾸는 근본 해결은 8절의 "다음 확인"에 남긴 별도 후속 작업이다.
