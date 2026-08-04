---
related_documents:
  - README.md
  - ../05-specs/data/second-expansion-data-contract.md
  - ../05-specs/data/lifecycle-rules.md
  - ../07-adr/data/data-012-second-expansion-retention-cleanup.md
  - ../08-planning/expansion-2-task-breakdown.md
  - ../08-planning/second-expansion-baseline-review.md
---

# PR #131 리뷰 트러블슈팅: 2차 확장 식별 제거와 착수 게이트

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#131 2차 확장 공통 스키마·권한·생명주기 기반](https://github.com/team-youngkk/masit-on/pull/131) |
| 작성자 | jinyp01 |
| 처리 일자 | 2026-08-04 |
| 범위 | 제보·신고 식별 제거 시각 보장과 E2-T01 구현 게이트 리뷰 |
| 주 문제 유형 | 데이터베이스, Git·운영 게이트 |
| 기존 기록 | [PR #129 기록](pr-129-deploy-cutover-and-rate-limit-review.md)을 확인했다. 배포 전환 안전장치와 자동 테스트는 재사용 가능한 근거지만, 해당 기록도 운영 환경 검증을 남은 사항으로 명시해 E2-T01 통과 증거로 대체할 수 없었다. 식별 제거 시각 제약과 같은 기존 기록은 없었다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [식별 제거 시각을 함께 강제](https://github.com/team-youngkk/masit-on/pull/131#discussion_r3709828086) (P2, 이우람) | 부모 회원을 직접 삭제해도 `member_id=NULL`과 `member_unlinked_at=now`가 함께 기록되게 함 | 데이터베이스 | 수정 필요 | `submission`·`report`의 두 열을 정확한 쌍으로 강제하고, 회원 삭제 전 두 요청을 unlink하는 PostgreSQL 트리거를 추가했다. 직접 회원 삭제와 잘못된 단독 NULL 갱신 회귀 테스트를 보강했다. | `Expansion2FlywayMigrationIntegrationTest`, `MemberDeletionCleanupPostgreSqlIntegrationTest` 통과 |
| [E2-T01 구현 게이트를 먼저 통과](https://github.com/team-youngkk/masit-on/pull/131#discussion_r3709848370) (P1, 김인안) | 선행 운영 검증과 owner 승인 뒤 권위 문서를 통과 상태로 동기화하거나 그 전까지 구현 PR 병합을 중단 | Git·운영 게이트 | 결정 필요 | 코드나 계약 상태를 임의로 변경하지 않고 PR을 Draft로 전환한다. 스레드는 미해결로 유지한다. | `implementation_gate: Blocked`, PR #126 본문의 Blocked 판정, 4개 선행 PRD/API의 `status: draft`, PR #129 기록의 운영 검증 잔여 사항을 확인 |

## 3. 문제 현상과 발생 조건

### 3.1 부모 삭제가 식별 제거 시각을 남기지 않음

- 오류 메시지: `ck_submission__member_unlinked`와 `ck_report__member_unlinked` 위반이 발생하지 않음
- 발생 환경: PostgreSQL 17.10, V3 마이그레이션 적용 스키마
- 재현 조건: 제보 또는 신고가 회원을 참조하는 상태에서 `member_account` 행을 직접 삭제한다.
- 실제 결과: FK의 `ON DELETE SET NULL`이 `member_id`만 NULL로 만들고 `member_unlinked_at`은 NULL로 남긴다. 기존 CHECK는 이 상태를 허용했다.
- 기대 결과: 데이터 계약대로 식별 연결과 제거 시각이 같은 트랜잭션에서 함께 갱신돼야 한다.
- 영향 범위: 탈퇴·보존 정리 감사 시점, 직접 부모 삭제 경로의 데이터 정합성

### 3.2 구현 선행 게이트가 문서상 Blocked임

- 오류 메시지: 없음
- 발생 환경: PR #131, `feature/e2-t02-common-foundation` → `develop`
- 재현 조건: 권위 계획·기준선 문서와 PR #126의 판정을 현재 PR의 구현 범위와 대조한다.
- 실제 결과: 구현 코드와 V3가 존재하지만 `implementation_gate`는 `Blocked`이고, 선행 PRD/API 네 문서는 `draft`다. PR #126도 운영 검증이 남아 게이트를 유지한다고 명시한다.
- 기대 결과: 문서 owner의 계약 상태 승인과 E2-T01 운영 증거가 먼저 기록된 뒤 게이트를 통과하고 E2-T02를 병합한다.
- 영향 범위: PR 병합 가능 여부와 후속 E2 Task의 기준 스키마

## 4. 근본 원인

3.1은 `ON DELETE SET NULL` FK와 Application 탈퇴 Command의 명시적 unlink를 함께 두면서, FK가 독립적으로 실행될 때 시각 열까지 채우는 DB 동작을 정의하지 않은 것이 원인이다. 기존 CHECK도 `member_id IS NULL OR member_unlinked_at IS NULL`이라 `NULL/NULL`을 허용해 계약 위반을 차단하지 못했다.

3.2는 이슈 #105가 닫힌 사실을 E2-T01 통과로 해석했지만, 실제 완료 PR #126은 자동 검증 결과만 보강하고 운영 검증과 owner 승인이 남아 `Blocked`를 유지한다고 명시한다. 이슈 상태와 권위 문서의 게이트 판정을 별개 증거로 확인하지 않은 것이 원인이다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| 기존 부모 삭제 통합 테스트와 V3 CHECK 대조 | 회원 삭제 후 `member_id IS NULL`만 확인했고 `member_unlinked_at`은 확인하지 않음 | 리뷰 지적 재현, 두 열의 쌍과 제보·신고 모두 검증 |
| FK를 `RESTRICT`로 바꾸는 대안 검토 | 데이터 계약이 `ON DELETE SET NULL`을 명시 | 계약 변경이므로 기각하고 삭제 전 트리거 채택 |
| Application 탈퇴 Command만 유지하는 대안 검토 | 직접 부모 삭제와 FK 경로에서는 시각 누락 | 모든 DB 삭제 경로를 보장하지 못해 기각 |
| PR #126, 이슈 #105, 기준선 문서, 선행 PRD/API 상태 확인 | 이슈는 닫혔지만 PR과 문서는 명시적으로 Blocked, 네 문서는 draft | 게이트를 임의 통과시키지 않고 결정 필요로 유지 |
| PR #129 트러블슈팅 기록 확인 | 전환 안전장치와 자동 검증은 있으나 실제 운영 확인이 남음 | E2-T01 운영 증거를 대체하지 못함 |

## 6. 최종 해결

- 변경 내용: `submission`·`report`의 식별 열 CHECK를 `member_id`와 `member_unlinked_at`의 정확한 반대 NULL 쌍으로 강화했다.
- 변경 내용: `member_account` 삭제 전에 두 요청 테이블을 `member_id=NULL`, `member_unlinked_at=CURRENT_TIMESTAMP`로 갱신하는 트리거를 V3에 추가했다.
- 변경 내용: 직접 회원 삭제 뒤 제보·신고 모두 제거 시각이 존재하는지, 시각 없이 `member_id`만 NULL로 바꾸면 거부되는지 PostgreSQL 테스트를 추가했다.
- 변경 내용: E2-T01은 문서 owner와 운영 담당자의 증거가 필요한 외부 결정이므로 PR을 Draft로 전환하고 스레드를 미해결 상태로 유지한다.
- 선택 이유: 트리거는 확정된 `ON DELETE SET NULL` 계약을 바꾸지 않으면서 Application Command와 직접 FK 삭제 모두 같은 불변 조건을 지킨다. 게이트는 구현자가 자체 승인할 수 없는 선행 통제이므로 변경하지 않는다.
- 변경 파일: `src/main/resources/db/migration/V3__add_expansion_2_schema.sql`, `src/test/java/com/masiton/Expansion2FlywayMigrationIntegrationTest.java`, `docs/troubleshooting/README.md`, 이 문서
- 고려한 대안: FK `RESTRICT`는 계약 변경이 필요하고, Application 계층만의 unlink는 DB 직접 삭제 경로를 보장하지 못해 채택하지 않았다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `./gradlew test --tests com.masiton.Expansion2FlywayMigrationIntegrationTest --tests com.masiton.member.application.MemberDeletionCleanupPostgreSqlIntegrationTest` | 통과, 10건 | V3 전진·빈 DB 적용, 직접 회원 삭제 시 두 요청의 식별 제거 시각, 탈퇴·보존 cleanup 회귀 |
| `./gradlew clean build` | 통과, 538건 | 전체 단위·PostgreSQL·Redis·WireMock·아키텍처 회귀, 실패·오류 0건 |
| `git diff --check` | 통과 | 공백 오류 없음 |

## 8. 재발 방지 및 다음 확인

- 재발 방지: 부모 삭제 정책 테스트가 nullable FK뿐 아니라 연동되는 감사·시각 열의 불변 조건도 함께 검증한다.
- 다음 확인: E2-T01 담당자 이우람과 기본 리뷰어 김인안, 회원·개인화 계약 owner가 운영 검증 및 문서 상태를 승인한 뒤 `implementation_gate`와 추적표를 별도 선행 변경에서 동기화한다. 추적 대상은 [이슈 #105](https://github.com/team-youngkk/masit-on/issues/105)다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| 직접 회원 삭제 후 식별 제거 시각 누락 | 부모 삭제 회귀 1건에서 누락 상태 허용 | PostgreSQL 마이그레이션 통합 테스트 | 0건, 제보·신고 모두 시각 존재 | 계약 위반 경로 차단 | E2-T02 담당자, PR #131 검증 시점 |
| E2-T01 운영 게이트 | Blocked | 권위 문서와 운영 실행 증거 대조 | 확인 예정 | 결정 전 비교 불가 | E2-T01 담당·리뷰어, 이슈 #105 후속 결정 시점 |

## 10. 남은 사항

- E2-T01 게이트 스레드는 문서 owner 승인과 운영 검증 증거가 없어 해결 처리하지 않는다.
- 게이트가 통과될 때까지 PR #131은 Draft 상태를 유지하며 병합하지 않는다.
