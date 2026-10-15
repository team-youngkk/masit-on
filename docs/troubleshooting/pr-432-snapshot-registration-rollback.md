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
| [같은 Snapshot의 등록 참조 보존](https://github.com/team-youngkk/masit-on/pull/432#discussion_r4278167816) | 등록 단위 참조를 CAS로 지운 뒤에도 같은 Snapshot의 `registered_*` 참조가 남으면 공개 자원과 방문 태그를 보존해야 함 | 데이터베이스 | 수정 필요 | 공유 자원 조회에서 Snapshot ID 제외 조건을 제거하고 PostgreSQL 회귀 테스트를 추가 | `AutoRegisterRestaurantReusePostgreSqlIntegrationTest`에서 동일 Snapshot 작업 참조가 있을 때 네 자원의 공개 상태가 유지됨을 검증 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: 없음
- 발생 환경: PostgreSQL 통합 테스트, PR #432의 단위 롤백 경로
- 재현 조건: 하나의 Snapshot에 등록 단위와 작업 수준 `registered_*` 참조가 같은 Restaurant·Creator·Video·Visit를 가리키고, 롤백 CAS가 등록 단위 참조를 먼저 비운다.
- 실제 결과: 공유 조회가 같은 Snapshot을 제외하여 남은 작업 수준 참조를 보지 못하고, 생성 자원을 PRIVATE로 바꾸거나 Visit 태그를 삭제할 수 있다.
- 기대 결과: 같은 Snapshot 작업 수준 참조가 남아 있는 동안 공유 자원으로 판단해 공개 상태와 Visit 태그를 보존한다.
- 영향 범위: 등록 단위 롤백의 정식 콘텐츠 공개 상태 및 방문 태그

## 4. 근본 원인

`JdbcAiRegisteredContentStore.isShared()`가 다른 Snapshot의 `registered_*`만 검사하도록 `id <> snapshotId` 조건을 사용했다. 롤백 경로에서는 단위 참조가 CAS로 이미 지워질 수 있으므로, 동일 Snapshot의 작업 수준 참조도 생존한 참조로 계산해야 한다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| 기존 PR #244 트러블슈팅 기록 검색 | 다른 등록 단위 참조 및 보상 경계는 다뤘으나 Snapshot 작업 참조 누락은 없음 | 동일 원인으로 오인하지 않고 별도 회귀 기록 작성 |
| `isShared()`의 단위 CAS 이후 조회 조건 확인 | 단위 CAS 뒤 Snapshot 조회가 같은 Snapshot을 제외함 | 같은 Snapshot도 공유 참조로 계산하도록 수정 |

## 6. 최종 해결

- 변경 내용: 자원별 Snapshot 참조가 어느 Snapshot에서든 존재하면 공유로 판단한다.
- 선택 이유: 작업 수준 참조가 유효한 동안 그 자원은 삭제·비공개 전환 대상이 아니기 때문이다.
- 변경 파일: `src/main/java/com/masiton/orchestration/infrastructure/rollback/JdbcAiRegisteredContentStore.java`, `src/test/java/com/masiton/orchestration/application/command/AutoRegisterRestaurantReusePostgreSqlIntegrationTest.java`

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `./gradlew.bat test --tests '*AutoRegisterRestaurantReusePostgreSqlIntegrationTest' --no-daemon --console=plain` | 통과 | PostgreSQL 통합 테스트 12건과 ArchUnit 검사 10건 통과. 동일 Snapshot의 작업 참조가 있는 롤백도 통과 |

## 8. 재발 방지 및 다음 확인

- 재발 방지: 같은 Snapshot 작업 수준 참조를 함께 만들고 롤백 후 공개 상태를 확인하는 PostgreSQL 회귀 테스트 추가
- 다음 확인: 없음

## 9. 도입 전후 비교 지표

해당 없음. 이 결함은 참조 무결성과 롤백 상태를 다루며 배포 확장 뒤 비교할 운영 빈도 지표가 이 수정의 효과를 직접 측정하지 않는다.

## 10. 남은 사항

- 없음
