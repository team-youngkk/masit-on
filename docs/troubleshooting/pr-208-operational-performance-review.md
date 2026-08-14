---
related_documents:
  - ../07-adr/quality/perf-002-operational-participant-load-testing.md
  - ../07-adr/adr-traceability.md
  - ../08-planning/issue-190-operational-performance-result.md
  - ../../perf/operational-fixture/README.md
  - ./pr-140-participation-notification-review.md
  - ./pr-185-e3-t13-final-gate-review.md
---

# PR #208 리뷰 트러블슈팅: 운영 fixture cleanup 참조 보호와 성능 추적성

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#208 운영 참여자 전용 성능 검증 절차와 결과를 기록한다](https://github.com/team-youngkk/masit-on/pull/208) |
| 작성자 | 이우람 (`w00lam`) |
| 처리 일자 | 2026-08-14 |
| 범위 | 최초 미해결 인라인 리뷰 스레드 3건의 cleanup 다형성 참조 보호, ADR 요구사항 역추적, seed 안내 문구 보완 |
| 주 문제 유형 | 데이터베이스 / 기타(문서·검증 도구) |
| 기존 기록 | `docs/troubleshooting`에서 제보·신고 알림 원자성([PR #140](./pr-140-participation-notification-review.md))과 최종 게이트 증거 범위([PR #185](./pr-185-e3-t13-final-gate-review.md))를 확인했다. PR #208 자체 기록은 없어 새 기록을 추가한다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [cleanup 다형성 신고 참조](https://github.com/team-youngkk/masit-on/pull/208#discussion_r3782173784) | fixture 맛집을 가리키는 report·완료 결과와 연결된 moderation history·notification을 cleanup 중단 조건에 포함 | 데이터베이스 | 수정 필요 | report의 `target_id`·`result_target_id`, submission의 완료 `result_target_id`, 연결된 history·notification을 검사하도록 `99-cleanup.sql`을 보강 | PostgreSQL에서 cleanup DO 블록이 새 조건을 포함해 컴파일되고 기존 fixture 부모 보호 가드까지 도달함 |
| [ADR 요구사항 역추적](https://github.com/team-youngkk/masit-on/pull/208#discussion_r3782175334) | NFR-PERFORMANCE-007·NFR-COST-001 행에 ADR-PERF-002 추가 | 기타 | 수정 필요 | `adr-traceability.md`의 두 요구사항 행에 ADR-PERF-002와 운영 직접 검증의 제한 경계를 추가 | 관련 요구사항·ADR frontmatter·추적표 대조 |
| [표준 seed 안내](https://github.com/team-youngkk/masit-on/pull/208#discussion_r3782282810) | 일반 측정 `perf/seed/`와 운영 직접 검증 `perf/operational-fixture/`를 모두 안내 | 기타 | 수정 필요 | k6 오류 문구에서 두 측정 환경의 seed 경로를 구분 | 시나리오 문구와 각 seed README 경로 대조 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: 없음. cleanup 보호 조건과 문서 추적성의 누락이다.
- 발생 환경: PR #208 `docs/issue-190-operational-performance`, PostgreSQL 17.10 스키마 V1~V4, 운영 직접 성능 검증 fixture.
- 재현 조건:
  - 실제 참여자가 fixture 맛집을 `report.target_type='RESTAURANT'`·`target_id`로 신고한다.
  - 완료된 submission/report 또는 moderation history가 fixture 맛집을 `result_target_id`로 가리킨다.
  - notification이 해당 submission/report를 참조한다.
  - ADR-PERF-002가 선언한 NFR을 추적표에서 검색한다.
  - 일반 `perf/seed/` 환경에서 운영 fixture만 안내하는 오류 문구를 본다.
- 실제 결과: 기존 cleanup은 fixture 회원 ID와 일부 관계만 검사해 비회원 참여자의 다형성 참조를 놓칠 수 있었고, 요구사항 역추적과 표준 seed 안내가 불완전했다.
- 기대 결과: 어떤 참여자든 fixture 맛집·완료 결과·관련 이력·알림을 참조하면 cleanup이 전체 트랜잭션을 중단하고, 두 부하 환경과 ADR 요구사항을 각각 추적할 수 있어야 한다.
- 영향 범위: 운영 데이터 고아 참조·이력 손실 위험, 성능 검증 재현성, NFR 추적성.

## 4. 근본 원인

cleanup의 보호 기준이 `member_id` 기반으로만 확장되어 다형성 대상인 `report.target_id`와 완료 결과인 `result_target_id`를 별도로 검사하지 않았다. `moderation_history`·`notification`은 요청 FK를 통해 연결되지만 그 요청이 fixture 맛집을 가리키는지 확인하는 보호 집합이 없었다. 문서 측면에서는 ADR-PERF-002를 추가하면서 관련 요구사항을 frontmatter에 선언했지만 요구사항→ADR 표를 같은 변경에서 동기화하지 않았고, k6 오류 문구도 운영 전용 경로만 안내했다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| GitHub GraphQL로 PR #208의 미해결 스레드 조회 | 미해결 스레드 3건, 모두 outdated 아님 | 세 요청 모두 현재 PR 범위의 수정 필요로 분류 |
| V3 스키마와 데이터 계약 대조 | report·submission의 다형성 target/result, moderation_history·notification FK 구조 확인 | 직접 대상·완료 결과·연결 이력·알림을 각각 보호 조건에 포함 |
| 기존 cleanup SQL의 `member_id` 조건 대조 | fixture 회원이 아닌 참여자의 fixture 맛집 신고·완료 결과를 막지 못함 | 보호 대상 ID 집합을 다형성 조건으로 확장 |
| 로컬 PostgreSQL에서 cleanup SQL 실행 | 로컬 DB에 활성 관리자와 fixture 부모가 없어 preflight는 중단됨. cleanup 직접 실행은 새 DO 블록을 컴파일한 뒤 예상 부모 건수 보호 가드에서 중단됨 | 운영 RDS fixture 전체 수명주기는 실행하지 못했으며, 배포 전 운영 DB에서 재확인해야 함 |
| ADR frontmatter·추적표와 k6 seed 문구 대조 | NFR-PERFORMANCE-007·NFR-COST-001과 표준 seed 경로가 누락됨 | 추적표와 오류 문구를 최소 변경 |

## 6. 최종 해결

- 변경 내용: `99-cleanup.sql`에 report target/result, submission 완료 결과, moderation history result·요청 FK, notification 요청 FK 보호 조건을 추가했다. `adr-traceability.md`에 ADR-PERF-002를 두 NFR 행에 연결하고, `normal-load-public-read.js`가 `perf/seed/`와 `perf/operational-fixture/`를 구분해 안내하도록 수정했다.
- 선택 이유: fixture 부모 삭제 전 다형성 참조를 데이터베이스에서 보수적으로 차단하면서 기존 운영 cleanup의 회원·FK 보호 구조를 유지하기 위해서다.
- 변경 파일: `perf/operational-fixture/99-cleanup.sql`, `docs/07-adr/adr-traceability.md`, `perf/k6/normal-load-public-read.js`, 이 기록과 `docs/troubleshooting/README.md`.
- 고려한 대안: 참여자 참조를 자동 삭제하거나 report·history·notification을 fixture 회원 기준으로만 정리하는 방식은 실제 사용자 데이터 손실·고아 이력 위험이 있어 채택하지 않았다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `git diff --check` | 통과 | SQL·문서·k6 변경에 공백 오류 없음 |
| 로컬 PostgreSQL 임시 report target 회귀 블록 | 통과 | 기존 맛집을 가리키는 `report.target_id`를 삽입한 뒤 보호 조건이 감지하는 것을 확인하고 트랜잭션을 rollback함 |
| `docker exec -i masiton-postgres psql ... -f perf/operational-fixture/99-cleanup.sql` | 부분 통과 | PostgreSQL이 새 cleanup DO 블록을 구문 분석하고 예상 fixture 부모 건수 보호 가드에서 중단함. 삭제는 발생하지 않음 |
| `docker exec -i masiton-postgres psql ... -f perf/operational-fixture/00-preflight.sql` | 미완료 | 로컬 DB에 활성 관리자 계정이 없어 preflight가 정상적으로 중단됨 |
| ADR 요구사항·seed 경로 대조 | 통과 | ADR-PERF-002의 NFR-PERFORMANCE-007·NFR-COST-001과 두 seed 경로가 문서·시나리오에 표시됨 |

## 8. 재발 방지 및 다음 확인

- 재발 방지: polymorphic target/result를 사용하는 cleanup은 회원 FK만으로 안전성을 판정하지 않고 대상 ID·완료 결과·연결 이력·알림을 함께 검사한다.
- 다음 확인: 활성 관리자와 기준 데이터가 있는 검증 환경에서 `00-preflight → 01-apply → 02-verify → 99-cleanup` 전체 수명주기를 재실행하고, 실제 참여자 report·notification 참조 시 cleanup이 중단되는지 확인한다. 담당자는 운영 검증 담당자(`w00lam`)이며 #190 운영 실행 기록에 결과를 남긴다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| fixture 맛집 참조 보호 범위 | 회원·일부 FK 참조 중심 | cleanup SQL의 보호 조건과 V3 schema 대조 | 직접 target/result + history·notification FK까지 검사 | 비회원 참여자의 다형성 참조를 놓치는 경로 제거 | `w00lam`, 다음 운영 fixture 실행, #190 |
| NFR-PERFORMANCE-007·NFR-COST-001 역추적 | ADR-PERF-002 누락 | `adr-traceability.md` 요구사항 행 검색 | ADR-PERF-002 연결 | 요구사항→ADR→운영 관찰 예외 경로 연결 | PR #208 반영 시점 |

## 10. 남은 사항

- 운영 DB와 동일한 기준 데이터가 없는 로컬 환경에서는 fixture 전체 수명주기를 실행하지 못했다. 운영 검증 환경에서 전체 실행과 실제 참조 중단 시나리오를 추가 확인해야 한다.
