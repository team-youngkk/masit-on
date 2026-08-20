---
related_documents:
  - ../05-specs/api/admin/ai-video-extraction-api.md
  - ../07-adr/integration/ai-001-video-extraction-candidate-boundary.md
  - pr-244-registration-unit-atomicity-review.md
---

# PR #259 리뷰 트러블슈팅: 등록 단위 일괄 폐기의 상태·감사 이력 불일치

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#259 fix: AI 영상 추출 등록 단위 예외 복구 흐름을 완성한다](https://github.com/team-youngkk/masit-on/pull/259) |
| 작성자 | `tjdgns0618` |
| 처리 일자 | 2026-08-20 |
| 범위 | 미해결 인라인 리뷰 7건(`jinyp01` 1건, `w00lam` 6건) |
| 주 문제 유형 | 애플리케이션 — 트랜잭션 경계·관측 가능성, 문서-코드 절 번호 불일치 |
| 기존 기록 | [PR #244 등록 단위 실행의 동시성·CONFIRM 원자성](pr-244-registration-unit-atomicity-review.md) 8절이 "여러 쓰기가 있으면 순수 DB 쓰기만 모은 `@Transactional` 커밋 서비스로 분리"하는 관례(`RegistrationUnitConfirmCommitService`)를 이미 확립해뒀다. 이번 PR의 `discardAllBlocked`가 이 관례를 따르지 않아 같은 유형의 결함이 재발했다 — 이번 기록에서 같은 패턴(`RegistrationUnitDiscardCommitService`)을 적용해 재발 방지 항목을 실제로 지켰다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [discardAllBlocked 감사 이력 실패 시 상태·감사 불일치 (P1, jinyp01)](https://github.com/team-youngkk/masit-on/pull/259#discussion_r3817800460) | `discard()`가 상태 전이(`registrationUnitStore.discard`)를 먼저 커밋한 뒤 감사 이력(`registrationUnitReviewStore.insert`)을 별도로 삽입해, 삽입 실패 시 상태만 바뀌고 감사 행이 없는 채로 예외가 조용히 삼켜짐 | 애플리케이션 | 수정 필요 | `RegistrationUnitDiscardCommitService`(`@Transactional`) 신설로 두 쓰기를 하나로 묶고, `discardAllBlocked`의 catch 블록에 경고 로그 추가 | `RegistrationUnitCommandServiceTest#discardAllBlocked_한단위가예상치못한오류로실패해도_나머지는계속폐기한다` |
| [retryUrl 조건 확장 (P5, w00lam)](https://github.com/team-youngkk/masit-on/pull/259#discussion_r3817807779) | 승인 코멘트 — 개선 요청 아님 | 애플리케이션 | 수정 불필요 | 리뷰어의 승인·칭찬 코멘트로, 변경을 요청하지 않음 | 해당 없음 |
| [Javadoc "API 3.7절" 표기 오류 (P3, w00lam)](https://github.com/team-youngkk/masit-on/pull/259#discussion_r3817807784) | `AdminAiExtractionQueryService.discardAllBlocked` Javadoc이 실제 문서 절 번호(3.10절)가 아닌 3.7절을 참조 | 애플리케이션 | 수정 필요 | `API 3.10절`로 정정 | 코드 리뷰(단순 문자열 정정, 별도 테스트 불필요) |
| [섹션 구분 주석 "API 3.7" 표기 오류 (P3, w00lam)](https://github.com/team-youngkk/masit-on/pull/259#discussion_r3817807788) | `RegistrationUnitCommandService`의 섹션 구분 주석도 같은 3.7→3.10 오기 | 애플리케이션 | 수정 필요 | `API 3.10 등록 단위 일괄 폐기`로 정정 | 코드 리뷰(단순 문자열 정정, 별도 테스트 불필요) |
| [개별 잠금·예외 격리 설계 (P5, w00lam)](https://github.com/team-youngkk/masit-on/pull/259#discussion_r3817807792) | 승인 코멘트 — 개선 요청 아님 | 애플리케이션 | 수정 불필요 | 리뷰어의 승인·칭찬 코멘트로, 변경을 요청하지 않음 | 해당 없음 |
| [원본 유튜브 영상 링크 (P5, w00lam)](https://github.com/team-youngkk/masit-on/pull/259#discussion_r3817807794) | 승인 코멘트 — 개선 요청 아님 | 애플리케이션 | 수정 불필요 | 리뷰어의 승인·칭찬 코멘트로, 변경을 요청하지 않음 | 해당 없음 |
| [프론트 retryActionAvailable 일원화 (P5, w00lam)](https://github.com/team-youngkk/masit-on/pull/259#discussion_r3817807797) | 승인 코멘트 — 개선 요청 아님 | 애플리케이션 | 수정 불필요 | 리뷰어의 승인·칭찬 코멘트로, 변경을 요청하지 않음 | 해당 없음 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: 없음(리뷰 시점 재현 코드, 실제 운영 장애 아님)
- 발생 환경: `RegistrationUnitCommandService.discardAllBlocked`(API 3.10절, PR #259 신규)
- 재현 조건: 작업에 `AUTO_BLOCKED` 등록 단위가 둘 이상 있고, 그중 하나의 감사 이력 삽입(`registrationUnitReviewStore.insert`)이 제약 위반 등 예상치 못한 `RuntimeException`으로 실패
- 실제 결과: `discard()`가 `registrationUnitStore.discard(...)`로 `review_status`를 이미 `MANUAL_OVERRIDE`로 커밋한 뒤 `registrationUnitReviewStore.insert(...)`에서 예외가 나면, `discardAllBlocked`의 `catch (RuntimeException) { continue; }`가 이 예외를 로그 없이 삼켜 다음 단위로 넘어간다. 그 결과 DB에는 폐기됐지만 응답 `discardedUnitIds`에는 없고 `ai_registration_unit_review`에 `DISCARD` 행도 없는 등록 단위가 남는다.
- 기대 결과: [API 3.10절](../05-specs/api/admin/ai-video-extraction-api.md)은 "폐기된 각 등록 단위는 감사 이력에 DISCARD 행이 남는다"고 명시한다. 상태 전이와 감사 이력은 항상 함께 반영되거나 함께 반영되지 않아야 한다.
- 영향 범위: 관리자 AUTO_BLOCKED 등록 단위 일괄 폐기(API 3.10절). 병합 전 PR이라 운영 영향은 없음.

## 4. 근본 원인

`discard()`가 `registrationUnitStore.discard(...)`(상태 전이)와 `registrationUnitReviewStore.insert(...)`(감사 이력)를 별도의 JDBC 호출로 순차 실행하고, 이 메서드를 감싸는 트랜잭션이 없었다. PR #244가 CONFIRM 경로에서 이미 겪고 고친 것과 같은 유형의 결함이다 — 여러 쓰기가 필요한 새 Application 경로를 작성할 때 트랜잭션 경계를 빠뜨리기 쉽다는 점이 [PR #244 기록](pr-244-registration-unit-atomicity-review.md) 8절의 재발 방지 항목 (2)에 이미 명시돼 있었으나, 이번 `discardAllBlocked`를 작성할 때 그 관례를 적용하지 않았다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| `RegistrationUnitConfirmCommitService` 기존 구현 확인 | CONFIRM 경로가 이미 "상태 전이·태그 연결·감사 이력 삽입을 하나의 `@Transactional` 메서드로 묶는" 패턴을 쓰고 있음 | DISCARD 경로에도 같은 패턴(`RegistrationUnitDiscardCommitService`)을 그대로 적용하기로 결정 |
| `discard()`의 유일한 두 호출부(`review()`의 DISCARD 분기, `discardAllBlocked`) 확인 | 둘 다 같은 private `discard()`를 거치므로, 이 메서드 내부만 고치면 두 경로 모두 원자성을 얻음 | `discard()`를 그대로 유지하되 내부 구현만 `discardCommitService.commit(...)` 호출로 교체 |
| private 메서드에 `@Transactional`을 직접 붙이는 방안 검토 | Spring AOP 프록시는 같은 클래스 내부 self-invocation(`this.discard(...)`)에 적용되지 않아 무의미함을 확인 | `RegistrationUnitConfirmCommitService`와 같은 별도 package-private `@Service` 빈으로 트랜잭션 경계를 분리 |

## 6. 최종 해결

- 변경 내용:
  - `RegistrationUnitDiscardCommitService`(신규, `@Transactional`)가 `registrationUnitStore.discard(...)`와 `registrationUnitReviewStore.insert(...)`를 하나의 트랜잭션으로 커밋. `expectedReviewStatus`가 더 이상 일치하지 않으면 아무것도 쓰지 않고 `false` 반환.
  - `RegistrationUnitCommandService.discard()`가 이 서비스에 위임하도록 변경.
  - `discardAllBlocked`의 catch 블록에 SLF4J 경고 로그(`log.warn(...)`) 추가로 예상치 못한 예외가 더 이상 완전히 소리 없이 삼켜지지 않게 함.
  - Javadoc·섹션 주석의 "API 3.7절/3.7" 표기를 실제 문서 절 번호 "API 3.10절/3.10"으로 정정(`AdminAiExtractionQueryService`, `RegistrationUnitCommandService`).
- 선택 이유: PR #244가 CONFIRM 경로에서 이미 검증한 "순수 DB 쓰기 여러 개는 별도 `@Transactional` 커밋 서비스로 묶는다"는 관례를 그대로 재사용해 새로운 패턴을 만들지 않았다. `discard()`가 private이라 자기 호출에는 `@Transactional`이 적용되지 않으므로, 별도 빈으로 분리하는 것이 유일하게 유효한 최소 변경이었다.
- 변경 파일:
  - `src/main/java/com/masiton/ai/application/RegistrationUnitDiscardCommitService.java`(신규)
  - `src/main/java/com/masiton/ai/application/RegistrationUnitCommandService.java`
  - `src/main/java/com/masiton/ai/application/AdminAiExtractionQueryService.java`
  - `src/test/java/com/masiton/ai/application/RegistrationUnitCommandServiceTest.java`
- 고려한 대안: `discardAllBlocked`의 catch 블록만 로깅하고 트랜잭션 분리는 하지 않는 방안도 검토했으나, 이는 관측 가능성만 개선할 뿐 상태·감사 불일치 자체(계약 위반)는 남기 때문에 채택하지 않았다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `./gradlew compileJava compileTestJava` | 통과 | 신규·변경 파일 컴파일 오류 없음 |
| `./gradlew test --tests "*RegistrationUnitCommandService*" --tests "*AdminAiExtractionQueryService*" --tests "*AdminAiVideoExtractionControllerApiTest*"` | 통과 | ArchUnit 포함 전체 통과. 신규 회귀 테스트(감사 이력 저장이 예상치 못한 오류로 실패해도 나머지 단위는 계속 폐기) 포함 |

## 8. 재발 방지 및 다음 확인

- 재발 방지: [PR #244 기록](pr-244-registration-unit-atomicity-review.md) 8절의 재발 방지 항목이 이미 "여러 쓰기가 있으면 순수 DB 쓰기만 모은 `@Transactional` 커밋 서비스로 분리"를 명시하고 있었다. 이번 PR은 그 항목을 실제로 지켜 `RegistrationUnitDiscardCommitService`를 추가했다 — `RegistrationUnitCommandService`에 새 다중-쓰기 경로를 추가할 때는 이 관례(및 두 기록에 남긴 다른 재발 방지 항목)를 먼저 확인한다.
- 다음 확인: 없음.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| 등록 단위 일괄 폐기의 상태·감사 불일치 발생 건수 | 해당 없음 — 병합 전 PR이라 운영 트래픽 기준값이 없음 | - | - | 해당 없음 | 담당자 `tjdgns0618`, 운영 배포 뒤 `discardAllBlocked` 관련 경고 로그 발생 빈도를 1회 확인 예정(추적 이슈 없음, 필요 시 신규 등록) |

## 10. 남은 사항

없음. 7개 스레드 모두 수정·확인 완료 후 답글·해결 처리한다.
