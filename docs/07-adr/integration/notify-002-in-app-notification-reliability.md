---
id: ADR-NOTIFY-002
title: 서비스 내 사용자 알림의 저장 신뢰성 경계
status: Accepted
decision_date: 2026-08-03
owners:
  - 이우람
reviewers:
  - 김인안
related_requirements:
  - FR-NOTIFICATION-001
  - FR-NOTIFICATION-002
  - FR-NOTIFICATION-003
  - FR-NOTIFICATION-004
  - BR-NOTIFICATION-001
  - BR-NOTIFICATION-004
  - NFR-INTEGRITY-005
  - NFR-RELIABILITY-004
  - NFR-PRIVACY-005
related_documents:
  - ../../04-product/prd/notification/user-notification.md
  - ../../05-specs/api/notification/notification-api.md
  - ../../05-specs/api/participation/submission-report-api.md
  - ../../05-specs/data/second-expansion-data-contract.md
  - ../../02-analysis/second-expansion-workstreams.md
  - ../security/auth-005-member-action-mail-outbox.md
  - ../adr-backlog.md
  - ../adr-index.md
  - ../adr-traceability.md
supersedes: []
superseded_by: null
---

# ADR-NOTIFY-002 서비스 내 사용자 알림의 저장 신뢰성 경계

## 1. 상태

Accepted

## 2. 결정 요약

제보·신고 처리 결과는 외부 전달이 아닌 서비스 내 `notification` 행으로만 고지한다. 상태 전이·ModerationHistory·Notification을 같은 PostgreSQL 트랜잭션에서 커밋하며 Outbox, Worker, 재시도 Queue, DLQ, FCM·이메일·웹 푸시를 도입하지 않는다.

## 3. 배경과 결정 문제

사용자 알림은 회원이 시작한 요청의 처리 상태를 로그인 뒤 알림함에서 확인하는 기능이다. `IN_REVIEW`, `ACCEPTED`, `REJECTED`, `COMPLETED` 전환과 알림은 함께 성공하거나 실패해야 한다.

[ADR-AUTH-005](../security/auth-005-member-action-mail-outbox.md)는 Action Token과 외부 이메일 전달 사이의 이중 쓰기를 해결하기 위한 좁은 Outbox 결정이다. 단일 소비 Token에 종속되지 않는 사용자 알림으로 그 전달 의미와 재시도 정책을 확대할 수 없다.

## 4. 고려한 선택지

- 상태 전이 트랜잭션에서 서비스 내 Notification 직접 저장
- DB Outbox에 기록하고 내부 Worker가 Notification 생성
- Outbox와 FCM·이메일 외부 전달, 자동 재시도·DLQ

## 5. 결정

- WS-12 상태 전이 Application Service가 허용 전이를 검증하고 같은 DB 트랜잭션에서 ModerationHistory와 Notification을 저장한다.
- Notification 저장 실패 시 상태 전이와 이력도 rollback한다. 재요청은 현재 상태와 `(request, status)` 고유 제약에 수렴한다.
- Notification은 읽음·목록·보존을 가진 최종 사용자 기록이다. 별도 전달 상태와 Outbox event가 아니다.
- 탈퇴로 요청의 회원 연결이 제거된 뒤에는 알림을 만들지 않는다.
- 외부 채널, Preference, DeviceToken, 메시지 브로커, Worker, DLQ를 만들지 않는다.

## 6. 선택 근거

상태와 알림이 같은 PostgreSQL 안에 있으므로 한 트랜잭션이 이중 쓰기 문제를 제거한다. 커밋 뒤 수행할 외부 부수효과가 없어 Outbox의 유실 방지 가치가 없고, Worker를 추가하면 오히려 사용자가 상태는 봤지만 알림은 아직 없는 지연 상태와 운영 복구 책임이 생긴다.

## 7. 트레이드오프

알림 저장 장애가 관리자 상태 전이도 실패시켜 가용성을 낮출 수 있다. 대신 처리 상태만 앞서 커밋되는 불일치를 허용하지 않는다. 외부 푸시가 없어 사용자는 서비스에 접속해야 알림을 확인하며 즉시 전달 SLA는 제공하지 않는다.

## 8. 강제 규칙

- 상태 전이와 알림 사이에 비동기 이벤트를 끼우지 않는다.
- 같은 요청·상태 알림은 한 건만 저장하고 중복 재시도에서 본문·읽음 상태를 덮어쓰지 않는다.
- 회원 입력 원문, 관리자 내부 메모와 신고자 식별자를 알림 Snapshot에 복제하지 않는다.
- ADR-AUTH-005의 테이블·Worker·암호화 Token 전달 정책을 재사용하지 않는다.
- FCM 활성화 전 [ADR-NOTIFY-001](../adr-backlog.md#adr-notify-001-fcm-푸시-알림)의 조건을 별도로 충족한다.

## 9. 구현·운영 영향

추가 인프라와 외부 비밀정보는 없다. WS-12가 트랜잭션 orchestration을 소유하고 WS-13이 Notification 생성 규칙·저장소·읽음·보존을 소유한다. 상태-알림 rollback, 고유 제약 충돌과 cleanup 실패를 구분해 관측한다.

## 10. 검증 방법

- Notification 저장 실패를 주입해 상태와 이력이 함께 rollback되는지 검증한다.
- 같은 상태 전이의 동시·반복 요청이 상태 이력과 알림 한 건으로 수렴하는지 검증한다.
- 다른 회원 접근, 탈퇴 후 생성 차단, 민감 입력 미복제와 로그 마스킹을 검사한다.
- Outbox·Worker·FCM·DeviceToken Bean, 설정과 테이블이 생기지 않았는지 아키텍처·마이그레이션 검증에 포함한다.

## 11. 재검토 조건

이메일·웹 푸시·FCM 등 외부 채널과 전달 SLA가 제품 범위로 승인되면 새 전달 신뢰성 ADR을 작성한다. 채널별 동의·해지, Token 수명주기, 전달 의미, 재시도·backoff, 중복 허용, DLQ·재처리, 비용과 비밀정보를 결정하기 전 기존 구조를 확대하지 않는다.

## 12. 관련 문서

- [사용자 알림 API](../../05-specs/api/notification/notification-api.md)
- [2차 확장 데이터 계약](../../05-specs/data/second-expansion-data-contract.md)
- [ADR-AUTH-005](../security/auth-005-member-action-mail-outbox.md)
- [ADR-NOTIFY-001](../adr-backlog.md#adr-notify-001-fcm-푸시-알림)
