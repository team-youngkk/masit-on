---
id: ADR-DATA-012
title: 2차 확장 보존 정책 정리 실행
status: Accepted
decision_date: 2026-08-03
owners:
  - 이우람
  - 김인안
related_requirements:
  - BR-SUBMISSION-004
  - BR-REPORT-004
  - BR-NOTIFICATION-003
  - NFR-RELIABILITY-004
  - NFR-PRIVACY-005
related_documents:
  - ../../05-specs/data/second-expansion-data-contract.md
  - ../../05-specs/data/lifecycle-rules.md
  - ../../05-specs/data/index-strategy.md
  - ../../04-product/prd/participation/user-submission-report.md
  - ../../04-product/prd/notification/user-notification.md
  - data-010-recent-view-retention-cleanup.md
  - ../adr-backlog.md
  - ../adr-index.md
  - ../adr-traceability.md
supersedes: []
superseded_by: null
---

# ADR-DATA-012 2차 확장 보존 정책 정리 실행

## 1. 상태

Accepted

## 2. 결정 요약

애플리케이션 내부 Spring Scheduler가 제보·신고의 1년 경과 회원 연결 제거, 알림 보존 정리와 24시간 멱등 기록 정리 Command를 정해진 주기로 호출한다. 각 Command는 1,000건 단위 트랜잭션을 사용한다. Spring Batch, 외부 Scheduler, advisory·Redis 분산 락과 메시지 Queue는 도입하지 않는다.

## 3. 배경과 결정 문제

회원이 다시 요청하지 않아도 개인정보·알림·기술 기록 보존 기한을 집행해야 한다. 사용자 GET에서 정리하면 읽기 전용 계약과 응답 안정성을 깨고, 생성·상태 전이 트랜잭션에 대량 정리를 섞으면 핵심 쓰기 지연과 부분 실패 범위가 커진다.

[ADR-DATA-010](data-010-recent-view-retention-cleanup.md)은 최근 기록 한 사례만 승인했으므로 2차 확장 정리에 자동 확대하지 않고 별도 결정한다.

## 4. 고려한 선택지

- 사용자 조회·쓰기 시점에 opportunistic cleanup
- 운영자가 수동 SQL 실행
- Spring Scheduler + 전용 Application Command
- Spring Batch 또는 외부 Scheduler·Worker

## 5. 결정

- 제보·신고 식별 제거는 매일 04:00 Asia/Seoul, 알림 정리는 매일 03:30, 멱등 기록 정리는 매시 15분에 실행한다. 시각은 운영 설정으로 덮어쓸 수 있다.
- Scheduler는 트리거만 담당하고 WS-12, WS-13과 공통 플랫폼의 각 Application Command가 정책·트랜잭션을 소유한다.
- 초기 단일 애플리케이션 인스턴스를 전제로 최대 1,000건씩 commit한다. 각 조건부 갱신·삭제는 중복 실행돼도 같은 결과로 수렴한다.
- 실패한 배치는 성공으로 표시하지 않고 지표·운영 알림을 남긴 뒤 다음 주기에 다시 처리한다.
- 조회 API는 보존 범위만 필터링하며 cleanup을 호출하거나 쓰지 않는다.

## 6. 선택 근거

현재 배포와 저장소가 Spring Boot·PostgreSQL 중심이라 새 프레임워크 없이 보존 의무를 사용자 요청과 분리할 수 있다. 작업은 단순 범위 갱신·삭제이고 재시작 지점과 복잡한 Step 이력이 필요하지 않아 Spring Batch의 운영 비용이 이점보다 크다.

## 7. 트레이드오프

주기 사이에는 물리 정리가 지연된다. 사용자 조회와 권한 판정은 cutoff를 즉시 적용해 만료 데이터를 노출하지 않는다. 애플리케이션 프로세스가 장기간 중지되면 정리도 중지되므로 최근 성공 시각을 감시해야 한다.

별도 실행 락이 없어 다중 인스턴스 전환 시 같은 후보를 중복 탐색할 수 있다. 현재 단일 인스턴스에서는 발생하지 않으며, 전환 시 실제 경합과 처리 비용을 근거로 리더 선출이나 DB 락을 재검토한다.

## 8. 강제 규칙

- cleanup을 사용자 GET·상태 전이·생성 트랜잭션에 포함하지 않는다.
- Clock과 cutoff를 주입 가능하게 하고 같은 cutoff 반복 실행은 멱등해야 한다.
- 회원·요청·알림 ID와 본문을 운영 로그에 남기지 않는다.
- Spring Batch, 외부 Worker, Redis 락은 활성화 조건과 새 ADR 없이 추가하지 않는다.
- 회원 탈퇴의 즉시 정리는 주기 작업을 기다리지 않고 탈퇴 Command가 수행한다.

## 9. 구현·운영 영향

단일 애플리케이션 안에 세 트리거가 추가된다. 최근 성공 시각, 대상·처리 수, 소요 시간과 실패 유형을 비식별 지표로 남긴다. 식별 제거 48시간, 알림 정리 48시간, 멱등 기록 정리 2시간 이상 성공이 없으면 운영 경고 대상으로 삼는다.

## 10. 검증 방법

- cutoff 직전·동일·직후, 최신 200개 경계와 회원별 분리를 PostgreSQL 통합 테스트한다.
- 1,000건 초과 데이터의 여러 commit과 같은 작업의 중복 실행을 검증한다.
- 중간 실패 뒤 다음 실행이 남은 행만 처리하는지 확인한다.
- 조회 API가 쓰기 쿼리와 cleanup Command를 호출하지 않는지 검증한다.

## 11. 재검토 조건

다중 인스턴스로 전환하거나, 한 주기 처리량이 따라가지 못하거나, 실행 이력·부분 재시작·의존 Step이 필요해지면 DB advisory lock·리더 선출·Spring Batch·외부 Scheduler를 새 ADR로 비교한다.

## 12. 관련 문서

- [2차 확장 데이터 계약](../../05-specs/data/second-expansion-data-contract.md#91-보존-작업-운영-계약)
- [ADR-DATA-010](data-010-recent-view-retention-cleanup.md)
- [ADR-AUTO-001](../adr-backlog.md#adr-auto-001-자동-수집과-배치-처리)
