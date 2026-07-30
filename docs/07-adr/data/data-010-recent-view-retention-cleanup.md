---
id: ADR-DATA-010
title: 최근 본 맛집 보존 기간 정리 실행
status: Accepted
decision_date: 2026-07-30
last_reviewed: 2026-07-30
owners:
  - 박진영
related_requirements:
  - FR-RECENT-002
  - NFR-PRIVACY-004
  - NFR-TEST-004
related_documents:
  - ../../05-specs/data/lifecycle-rules.md
  - ../../05-specs/data/index-strategy.md
  - ../../05-specs/data/table-definitions.md
  - ../../05-specs/api/personal/personal-restaurant-api.md
  - ../../08-planning/expansion-1-implementation-plan.md
  - ../../08-planning/expansion-1-task-breakdown.md
  - ../adr-backlog.md
  - ../adr-index.md
  - ../adr-traceability.md
  - ../platform/deploy-002-validation-deployment-before-expansion.md
supersedes: []
superseded_by: null
---

# ADR-DATA-010 최근 본 맛집 보존 기간 정리 실행

## 1. 상태

Accepted

이 ADR은 최근 본 맛집의 30일 보존을 신규 조회와 독립적으로 집행하는 실행 경계를 확정한다. 자동 수집·외부 데이터 동기화를 Post-MVP로 두는 [ADR-AUTO-001](../adr-backlog.md#adr-auto-001-자동-수집과-배치-처리)은 그대로 유지한다.

## 2. 결정 요약

Spring Scheduler가 하루 한 번 이상 `recent_restaurant_view`의 30일 경과 행을 삭제하는 idempotent cleanup Command를 호출한다. 최근 기록 upsert는 회원별 최신 50개 상한만 정리하고, 목록 GET은 읽기 전용 필터링만 수행한다.

## 3. 배경

최근 기록을 upsert할 때만 30일 경과 행을 삭제하면 마지막 상세 조회 뒤 활동하지 않는 회원의 기록은 정리 기회를 얻지 못한다. 반대로 목록 GET에서 삭제하면 조회 트랜잭션의 `readOnly` 계약을 깨고, 삭제만 커밋된 뒤 조회가 실패하는 부분 성공을 만들 수 있다.

초기 운영은 단일 애플리케이션 인스턴스이며, 별도 배치 서버·메시지 큐·Spring Batch·분산 락은 범위에 없다. 현재 데이터 규모에서는 애플리케이션 내부의 제한된 주기 실행으로 보존 정책을 집행할 수 있다.

## 4. 결정 문제

사용자가 다시 조회하거나 upsert하지 않아도 30일 보존을 지키면서, 사용자 GET과 외부 수집 자동화에 정리 책임을 섞지 않는 실행 방법은 무엇인가.

## 5. 고려한 선택지

- **최근 기록 upsert에서만 정리**: 별도 실행 기반이 없지만 비활성 회원의 만료 행을 삭제하지 못한다.
- **최근 기록 GET에서 정리**: 조회의 읽기 전용 계약과 실패 원자성을 위반한다.
- **Spring Scheduler가 cleanup Command 호출**: 별도 라이브러리·서비스 없이 사용자 요청과 독립적으로 실행할 수 있다.
- **Spring Batch 또는 외부 스케줄러 도입**: 이력·재시작에는 유리하지만 현재 데이터 규모와 단일 인스턴스에 비해 운영 복잡도가 크다.

## 6. 결정

Spring Scheduler와 전용 Application cleanup Command를 채택한다.

- Scheduler는 하루 한 번 이상 실행하며, 실행 시각은 운영 설정으로 둔다.
- Command는 서버 기준 현재 시각에서 30일 이전인 `last_viewed_at` 행을 물리 삭제한다.
- 30일 경과 행은 목록 GET에서 즉시 제외하고, 물리 삭제는 경과 후 24시간 안에 완료하는 것을 운영 목표로 한다.
- Command는 같은 cutoff로 반복 실행해도 결과가 같은 idempotent 동작이어야 한다.
- cleanup은 사용자 요청, 외부 API 호출, 자동 등록·동기화를 수행하지 않는다.
- 최근 기록 upsert는 대상 시각 갱신과 회원별 최신 50개 초과분 정리만 같은 Command 트랜잭션에서 수행한다.
- 목록 GET은 30일·공개 상태·50개 범위를 읽기 전용으로 필터링하며 cleanup을 호출하지 않는다.

## 7. 선택 근거

Spring Scheduler는 현재 Spring Boot 런타임 안에서 사용할 수 있어 새 외부 서비스나 배치 프레임워크를 요구하지 않는다. 실행 책임을 전용 Command로 분리하면 Scheduler는 트리거만 담당하고 데이터 생명주기 규칙과 트랜잭션은 개인화 도메인이 소유한다.

이 선택은 자동 수집·주기 동기화를 허용하지 않는다. 같은 스케줄 기술을 사용하더라도 입력 수집과 외부 동기화는 ADR-AUTO-001의 Post-MVP 경계에 남고, 이 ADR은 이미 저장된 최소 행동 데이터의 파기만 허용한다.

## 8. 트레이드오프

일 단위 실행이므로 30일 경과와 물리 삭제 사이에 최대 24시간의 지연이 생긴다. 사용자 조회에서는 cutoff로 즉시 숨기고, 물리 삭제 지연은 운영 지표로 감시한다.

초기 단일 인스턴스 전제에서는 별도 리더 선출이나 분산 락이 없다. 여러 애플리케이션 인스턴스로 확장하면 중복 실행 비용과 잠금 경합을 재검토해야 한다.

## 9. 적용 범위

WS-06 개인 맛집 관리의 `recent_restaurant_view` 생명주기와 FE-04/E1-T05 구현·테스트에 적용한다. 찜, 회원 탈퇴 정리, 외부 데이터 수집과 Creator 표시 정보 동기화에는 적용하지 않는다.

## 10. 강제 규칙

- cleanup 트랜잭션은 사용자 GET·상세 조회 트랜잭션과 분리한다.
- `last_viewed_at` cutoff 계산은 주입 가능한 서버 Clock을 사용한다.
- 실행 성공·실패·삭제 행 수·소요 시간을 비식별 운영 지표로 남기고 회원·맛집 식별자를 로그에 남기지 않는다.
- 실패를 성공으로 기록하지 않으며 다음 주기에서 재시도한다.
- Spring Batch, 별도 워커, 메시지 큐, 외부 API와 분산 락을 이 결정의 선행 구조로 추가하지 않는다.

## 11. 검증 방법

- 30일 경계 전·후 행과 비활성 회원 행을 포함한 PostgreSQL 통합 테스트로 삭제 대상을 검증한다.
- 같은 cutoff의 반복 실행과 삭제 대상이 없는 실행이 성공하는지 검증한다.
- cleanup 실패 뒤 다음 실행이 남은 대상 행을 삭제하는지 검증한다.
- 목록 GET이 30일 경과 행을 반환하지 않고 쓰기 쿼리나 cleanup Command를 실행하지 않는지 검증한다.
- `ix_recent_restaurant_view__cleanup_viewed(last_viewed_at)`를 사용하는 범위 삭제 실행계획을 대표 데이터로 확인한다.

## 12. 운영 영향

단일 애플리케이션 프로세스 안에 하루 한 번 이상의 정리 트리거가 추가된다. 실패·지연·삭제 행 수는 운영 관측 대상이며, 최근 성공 시각이 24시간을 넘기면 알림 대상으로 본다.

## 13. 재검토 조건

애플리케이션이 다중 인스턴스로 확장되거나, cleanup이 사용자 API 성능에 영향을 주거나, 한 번의 트랜잭션으로 운영 목표를 지키지 못하거나, 실행 이력·부분 재시작이 필요해지면 Spring Batch·외부 스케줄러·리더 선출 또는 분산 락을 새 ADR로 재검토한다.

## 14. 관련 문서

- [최근 본 맛집 생명주기](../../05-specs/data/lifecycle-rules.md#101-회원-개인화-관계-정리)
- [개인 맛집 관리 API](../../05-specs/api/personal/personal-restaurant-api.md)
- [1차 확장 구현 계획](../../08-planning/expansion-1-implementation-plan.md)
- [ADR-AUTO-001 자동 수집과 배치 처리](../adr-backlog.md#adr-auto-001-자동-수집과-배치-처리)
