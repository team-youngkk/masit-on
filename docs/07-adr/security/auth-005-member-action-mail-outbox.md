---
id: ADR-AUTH-005
title: 회원 Action 메일의 신뢰성 있는 전달 (Outbox)
status: Accepted
decision_date: 2026-07-31
owners:
  - 김인안
related_requirements:
  - FR-MEMBER-001
  - FR-MEMBER-002
  - FR-MEMBER-003
  - NFR-SECURITY-005
  - NFR-INTEGRITY-003
related_documents:
  - ../../05-specs/api/account/member-authentication-api.md
  - auth-002-member-jwt-refresh-token.md
  - auth-003-confirmation-token.md
  - ../data/data-004-flyway.md
  - ../adr-backlog.md
  - ../adr-index.md
  - ../adr-traceability.md
  - ../../05-specs/data/migration-plan.md
supersedes: []
superseded_by: null
---

# ADR-AUTH-005 회원 Action 메일의 신뢰성 있는 전달 (Outbox)

## 1. 상태

Accepted

이 ADR은 [ADR-EXT-002](../adr-backlog.md#adr-ext-002-자동-복원력과-신뢰성-이벤트-전달)(Conditional) 활성화 조건 중 "DB 커밋 뒤 유실되어서는 안 되는 알림·외부 동기화·후속 작업이 승인된다"에 해당하는 **회원 Action 메일(가입 인증·비밀번호 재설정) 발송 한 가지 사례만** 확정한다. 자동 재시도 상한·backoff, Circuit Breaker, 도메인 이벤트·메시지 브로커, Kakao·YouTube 등 다른 외부 Adapter의 복원력은 이 ADR의 범위가 아니며 ADR-EXT-002는 그 나머지 항목에 대해 계속 Conditional로 남는다.

## 2. 결정 요약

회원 가입·재발송·비밀번호 재설정에서 Action Token 발급과 메일 발송 요청을 같은 트랜잭션에서 암호화 Outbox 행으로 원자적으로 기록하고, 별도 `@Scheduled` Worker가 커밋 뒤 이 행을 claim해 실제 SMTP 발송을 수행한다.

## 3. 배경

[PR #74](https://github.com/team-youngkk/masit-on/pull/74) 리뷰에서 두 가지 결함이 지적됐다.

- 가입·재발송·재설정 메서드가 트랜잭션 안에서 SMTP를 동기 호출했다. 활성 계정만 실제로 메일을 보내고 존재하지 않거나 비활성인 계정은 보내지 않는 구조라, SMTP 실패가 그대로 전파되면 활성 이메일은 `5xx`, 그 외에는 `202`가 되어 [계정 상태 비노출 계약](../../05-specs/api/account/member-authentication-api.md)이 깨지고 계정 열거(enumeration)가 가능해진다.
- 메일 발송에 먼저 성공한 뒤 트랜잭션 커밋이 실패하면, 사용자는 원문 Token을 이미 받았지만 그 Token은 DB에 존재하지 않아 사용할 수 없는 이중 쓰기(dual write) 문제가 있다.

리뷰 논의 초반에는 이 요구가 Transactional Outbox나 자동 재시도 구현으로 확대되지 않아야 한다는 의견이 있었고, 이는 [ADR-EXT-002](../adr-backlog.md#adr-ext-002-자동-복원력과-신뢰성-이벤트-전달)가 Conditional로 남겨둔 범위와 일치한다. 그러나 두 결함 모두 "DB 커밋 뒤 유실되어서는 안 되는 후속 작업"이라는 ADR-EXT-002의 활성화 조건에 정확히 해당하고, 계정 상태·메일 발송 결과 비노출이라는 Critical 요구([NFR-SECURITY-005](../../01-requirements/non-functional-requirements.md#nfr-security-005-회원-인증-남용과-계정-열거-방지))를 단순 예외 억제로는 만족시킬 수 없어, 리뷰 과정에서 영속 Outbox로 귀결됐다. 구현(구 V3 구간의 `member_action_mail_outbox`, `MemberActionMailOutboxService`)은 이미 `develop`에 병합돼 있었으나, 이 결정을 확정하는 ADR은 지금까지 없었다. 이 문서는 이미 구현된 범위를 사후에 명시적으로 승인하고 경계를 고정한다.

## 4. 결정 문제

상태 비노출과 Token-메일 일관성을 지키면서, ADR-EXT-002가 막고 있는 일반적인 자동 복원력 프레임워크(Circuit Breaker, 메시지 브로커, 범용 재시도 정책)를 끌어들이지 않고 이 좁은 문제만 풀 수 있는가.

## 5. 고려한 선택지

- **예외를 삼키고 항상 `202`를 반환**: 메일 실패를 로그만 남기고 무시한다. 상태 비노출은 지키지만, 발송 성공 여부를 재현할 방법이 없고 커밋 실패 시 이중 쓰기 문제가 그대로 남는다.
- **범용 자동 복원력 프레임워크 도입(Circuit Breaker·메시지 브로커)**: ADR-EXT-002의 활성화 조건(측정된 실패율·호출량, 승인된 유실 불가 후속 작업) 중 후자에는 해당하지만 전자는 측정되지 않았고, Kakao·YouTube를 포함한 모든 외부 호출에 영향을 주는 결정이라 이번 문제의 범위를 넘어선다.
- **암호화 Outbox 테이블 + 전용 Worker(선택)**: Action Token 생성과 Outbox 기록을 같은 트랜잭션에 두어 원자성을 보장하고, 실제 발송은 트랜잭션 밖에서 별도 Worker가 담당한다. 발송 성공 여부와 무관하게 API는 항상 동일한 `202`를 반환할 수 있다.

## 6. 결정

- `member_action_mail_outbox`(구 V3 구간, 통합 후 `V2__add_expansion_1_schema.sql`)에 원문 Token 대신 AES-GCM 암호문(`encrypted_token`, `encryption_nonce`, `encryption_key_id`)만 저장하고, Action Token 발급과 같은 트랜잭션에서 원자적으로 기록한다. 두 쓰기 중 하나가 실패하면 전체가 rollback된다.
- `EMAIL_VERIFICATION` 메일은 혼동 문자를 제외한 영문 대문자·숫자 8자 코드를 본문에 표시하고, `PASSWORD_RESET` 메일은 기존 고엔트로피 불투명 Token을 전달한다. 형식은 달라도 암호화 Outbox·단일 소비·만료·재시도 계약은 동일하다.
- `MemberActionMailOutboxService`가 고정 주기(기본 1분, `masiton.member.action-mail.dispatch-interval`)로 `PENDING` 행을 `FOR UPDATE SKIP LOCKED`와 5분 lease(`locked_until`)로 claim해 복호화 후 발송하고, 성공하면 `SENT`로 표시한다.
- 발송 실패는 고정 1분 지연(`RETRY_DELAY_MINUTES`) 뒤 재시도로 예약한다. 최대 재시도 횟수·backoff·jitter는 별도로 두지 않는다 — 아래 8절의 근거로 필요하지 않다고 판단했다.
- claim 시점에 근거 Action Token이 이미 `ISSUED`가 아니거나 만료됐으면 발송하지 않고 `CANCELLED`로 전환한다(`cancelIneligible`, `confirmDelivery`).
- 이 결정은 회원 Action 메일(가입 인증, 비밀번호 재설정) 발송에만 적용한다. Kakao·YouTube 등 다른 외부 Adapter는 [ADR-EXT-001](../integration/ext-001-reference-verification.md)의 동기 호출·수동 재시도를 그대로 유지하며 이 ADR로 변경하지 않는다. Circuit Breaker, 메시지 브로커, 도메인 이벤트는 여전히 도입하지 않는다.

## 7. 선택 근거

- Action Token과 Outbox 행을 같은 트랜잭션에 두면 "메일은 갔지만 Token이 없는" 상태와 "Token은 있지만 메일이 영원히 안 가는" 상태 둘 다 구조적으로 없어진다. 트랜잭션이 rollback되면 Outbox 행도 함께 사라지고, 커밋되면 Worker가 반드시 나중에 claim할 수 있는 행이 남는다.
- 최대 재시도 상한이나 DLQ를 별도로 만들지 않은 이유는 이 Outbox의 각 행이 특정 Action Token 하나에 종속되고, 그 Token은 `member_action_token`에 대한 원자적 단일 소비 `UPDATE ... WHERE status = 'ISSUED' ... RETURNING`(`JdbcMemberActionTokenRepository`)과 만료 시각으로 이미 재사용이 불가능해지기 때문이다. `cancelIneligible`이 Token 만료·상태 변화를 감지해 `CANCELLED`로 전환하므로 재시도는 Token 수명이라는 이미 존재하는 경계로 자동 상한이 걸리며, 별도 재시도 횟수 정책을 새로 설계할 필요가 없다. (관리자 등록 확인 Token은 별도 테이블·별도 ADR인 [ADR-AUTH-003](auth-003-confirmation-token.md)이 다루며, 이 Outbox의 근거 Token과는 무관하다.)
- Worker를 메시지 브로커 대신 PostgreSQL 폴링으로 구현한 이유는 이 저장소가 이미 Flyway·PostgreSQL 단일 저장소 구조([ADR-DATA-004](../data/data-004-flyway.md))를 쓰고 있어 추가 인프라 없이 `SKIP LOCKED`로 동시 실행 안전성을 얻을 수 있기 때문이다. 호출량이 회원 가입·재설정 빈도에 비례하는 저트래픽이라 별도 Queue의 처리량 이점이 필요하지 않다.

## 8. 트레이드오프

- 발송까지 최대 `dispatch-interval` + 처리 시간만큼 지연이 생긴다. 가입 인증·재설정처럼 사용자가 메일을 확인하러 이동하는 사이에 자연히 소화되는 지연이라 수용한다.
- 원문 Token은 암호화 상태로만 저장되므로 키 손실 시 미발송 메일은 복구할 수 없다. `encryption_key_id`로 키 회전을 지원해 키 교체 자체는 안전하지만, 키 폐기와 미발송 잔여 행 처리 절차는 운영 절차로 별도 필요하다(12절).
- 폴링 Worker는 애플리케이션 인스턴스 안에서 동작하므로, 여러 인스턴스가 뜨면 각자 폴링한다. `SKIP LOCKED`는 동시 Worker 간 이중 claim만 막을 뿐, 발송 성공 후 `markSent` 실패나 5분 lease 만료가 겹치면 같은 메일이 다시 발송될 수 있다(전달 의미는 at-least-once, 10절 참고). 인스턴스 수가 늘면 유휴 폴링 쿼리도 늘어난다. 현재 단일 EC2 인스턴스 운영([ADR-DEPLOY-002](../platform/deploy-002-validation-deployment-before-expansion.md))에서는 후자의 영향이 없다.

## 9. 적용 범위

- 포함: 회원 가입 인증 메일, 비밀번호 재설정 메일의 발송 요청 기록·발송·재시도·만료 취소.
- 제외: Kakao·YouTube 등 관리자 등록 외부 호출([ADR-EXT-001](../integration/ext-001-reference-verification.md) 유지), 관리자 도메인의 다른 알림, 범용 Circuit Breaker·메시지 브로커·도메인 이벤트(ADR-EXT-002 나머지 항목은 계속 Conditional), 탈퇴 정리 작업과 세션 폐기 복구 작업(별도 재시도 작업으로 이 Outbox와 스키마·책임을 공유하지 않는다).

## 10. 강제 규칙

- Action Token 발급과 Outbox 행 기록은 같은 트랜잭션에서 원자적으로 수행한다. 둘 중 하나만 성공하는 경로를 만들지 않는다.
- Outbox에는 원문 Token을 저장하지 않는다. AES-GCM 암호문·nonce·key id만 저장한다.
- API 응답은 메일 발송 성공 여부와 무관하게 계정 상태 비노출 계약을 그대로 지킨다. Worker의 발송 실패가 API 응답을 바꾸지 않는다.
- claim은 `FOR UPDATE SKIP LOCKED`로 동시 Worker 간 이중 claim만 막는다. 발송 성공 후 `markSent` 실패나 lease 만료가 겹치면 같은 메일이 다시 발송될 수 있어 전달 의미는 at-least-once다. 이 중복은 근거 Action Token의 원자적 단일 소비로 안전하게 흡수된다 — 먼저 도착한 메일의 링크를 쓰면 이후 도착한 메일의 링크는 소비할 수 없다. 이 흡수 조건이 성립하지 않는 알림(단일 소비 Token에 종속되지 않는 알림)에는 이 Outbox를 그대로 재사용하지 않는다.

## 11. 금지 사항

- 이 ADR의 결정을 다른 외부 Adapter(Kakao·YouTube, 향후 추가될 알림)로 확대하지 않는다. 확대하려면 ADR-EXT-002의 나머지 활성화 조건(측정된 실패율·호출량 등)을 별도로 충족해야 한다.
- Circuit Breaker, 메시지 브로커, 도메인 이벤트, 범용 재시도 정책 도입은 여전히 금지한다([ADR-EXT-002](../adr-backlog.md#adr-ext-002-자동-복원력과-신뢰성-이벤트-전달) Conditional 유지).
- 재시도 횟수 상한·DLQ를 이 Outbox에 추가하려면, 8절의 "Token 수명이 자동 상한"이라는 근거가 더 이상 유효하지 않다는 관측(예: 장기 유효 Token 종류 추가)이 먼저 있어야 한다.

## 12. 구현 및 운영 영향

- `MemberActionTokenCipher`가 `MEMBER_ACTION_MAIL_ACTIVE_KEY_ID`·`MEMBER_ACTION_MAIL_ACTIVE_KEY`로 지정된 활성 키로만 암호화하고, `encryption_key_id`로 과거 키도 복호화할 수 있어 키 회전 중에도 이미 쌓인 미발송 행을 처리할 수 있다. 키는 저장소에 커밋하지 않고 배포 환경 비밀 관리 경로로 주입한다([README](../../../README.md) 최초 1회 절차).
- 키를 완전히 폐기(rotation 아닌 폐기)하기 전에는 해당 `encryption_key_id`를 가진 미발송(`PENDING`) 행이 없는지 확인한다. 남아 있으면 먼저 처리하거나 명시적으로 `CANCELLED` 처리한다.
- Worker 주기·초기 지연은 `masiton.member.action-mail.dispatch-interval`·`initial-dispatch-delay`로 설정하며 기본값은 각각 1분이다.

## 13. 검증 방법

- `MemberActionMailOutboxPersistenceIntegrationTest`: 가입 요청이 Action Token과 암호화 Outbox 행을 같은 트랜잭션으로 기록하는지, `PENDING` 행을 안전하게 claim·재시도·완료하는지, claim 뒤 Token이 revoke되면 전달 확인이 취소되고 `false`를 반환하는지 검증한다.
- `MemberActionMailOutboxServiceTest`: claim한 메일을 복호화해 전송 후 `SENT`로 완료하는지, 전송 실패 시 재시도로 예약하는지, claim 뒤 Token이 무효화되면 복호화·전송을 하지 않는지 검증한다.
- `MemberActionMailOutboxTransactionIntegrationTest`: Outbox 기록이 실패하면 회원 계정과 Action Token도 함께 rollback되는지 검증한다.

## 14. 재검토 조건

메일 외 다른 후속 작업(예: 관리자 알림, 다른 외부 동기화)에도 유실 방지가 필요해지면, 이 ADR을 확장하지 않고 ADR-EXT-002의 남은 활성화 조건을 별도로 충족한 새 ADR로 결정한다. 현재 폴링 방식으로 처리하지 못할 규모의 발송량이 관측되면 재검토한다.

## 15. 관련 문서

- [회원 계정·인증 API](../../05-specs/api/account/member-authentication-api.md)
- [ADR-AUTH-002 회원 JWT와 Refresh Token](auth-002-member-jwt-refresh-token.md)
- [ADR-AUTH-003 확인 Token](auth-003-confirmation-token.md)
- [ADR-EXT-001 관리자 외부 기준정보 확인 서비스](../integration/ext-001-reference-verification.md)
- [ADR Backlog](../adr-backlog.md)
