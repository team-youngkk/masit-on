---
id: ADR-AUTH-003
title: 관리자 등록 확인 Token의 저장·소비·재시도
status: Accepted
decision_date: 2026-07-27
owners:
  - 김인안
  - 이우람
related_requirements:
  - FR-ADMIN-002
  - FR-ADMIN-003
  - FR-ADMIN-004
  - NFR-SECURITY-001
  - NFR-INTEGRITY-002
  - NFR-INTEGRITY-003
  - NFR-RELIABILITY-002
related_documents:
  - ../../05-specs/api/admin/reference-data-api.md
  - ../../05-specs/api/common/error-contract.md
  - ../../05-specs/data/constraints.md
  - ../../05-specs/data/data-model.md
  - ../../06-architecture/application-flow.md
  - ../../06-architecture/transaction-boundaries.md
  - ../../06-architecture/security-boundary.md
  - ../data/data-001-postgresql.md
  - ../data/data-004-flyway.md
  - auth-007-unified-account-rbac-session.md
supersedes: []
superseded_by: null
---

# ADR-AUTH-003 관리자 등록 확인 Token의 저장·소비·재시도

## 1. 상태

Accepted

## 2. 결정 요약

맛집·Creator·Video 검증 미리보기의 확인 Token은 PostgreSQL에 저장하는 10분 수명의 불투명 일회용 Token으로 구현한다. Token 원문은 클라이언트에 한 번만 전달하고 서버에는 SHA-256 해시와 관리자·자원 종류·정규화 후보 Snapshot을 저장한다. Token 소비와 Entity 생성 또는 중복 완료 처리는 한 PostgreSQL 트랜잭션에서 원자적으로 수행한다.

최초 생성은 `201 Created`, 이미 생성에 성공한 동일 관리자·동일 Token 재시도는 새 부수 효과 없이 기존 Entity를 `200 OK`로 반환한다. 미리보기 이후 다른 요청이 동일 자원을 먼저 만들었다면 Token을 `DUPLICATE` 완료 상태로 전환하고 최초와 이후 요청 모두 같은 `409 DUPLICATE_*`와 기존 자원 정보를 반환한다.

## 3. 배경

관리자 등록은 외부 기준정보 검증과 내부 Entity 생성을 분리한다. 미리보기에서 받은 정규화 후보를 관리자가 확인한 뒤 생성 확정 API에는 `confirmationToken`만 제출한다. 따라서 서버는 후보 무결성, 관리자 결속, 10분 만료와 단일 사용을 보장하면서 외부 API를 다시 호출하지 않아야 한다.

생성 커밋 직후 HTTP 응답이 유실될 수 있으므로 단순히 모든 Token 재사용을 오류로 처리하면 관리자가 실제 생성 여부를 알기 어렵다. 반대로 Token을 상태 없이 서명만 하면 단일 사용과 생성 원자성을 위해 별도 재사용 저장소가 다시 필요하다.

## 4. 결정 문제

확인 Token의 후보 무결성·만료·단일 사용을 어떤 저장소에서 보장하고, Token 소비와 Entity 생성 및 네트워크 재시도를 어떤 원자성과 결과 계약으로 처리할 것인가.

## 5. 고려한 선택지

- 후보를 포함한 서명된 무상태 Token
- 서명 Token과 Redis 재사용 방지 레코드
- PostgreSQL 서버 저장형 불투명 Token

## 6. 결정

### 6.1 발급과 저장

- 암호학적으로 안전한 난수 생성기로 최소 256-bit Token을 만들고 Base64url 무패딩 문자열로 전달한다.
- Token 원문은 응답 이후 서버에 저장하지 않는다. PostgreSQL에는 SHA-256 해시를 고유값으로 저장한다.
- 레코드는 관리자 식별자, 자원 종류, 후보 스키마 버전, 검증된 외부 동일성 값과 정규화 후보 JSONB Snapshot, 발급·만료 시각, 처리 결과, 완료 시각과 결과 자원 식별자를 가진다.
- 후보 Snapshot은 서버가 외부 확인과 입력 검증을 마친 값이며 생성 확정 시 클라이언트 값이나 외부 API 응답으로 다시 구성하지 않는다.
- `READY`에만 Token을 발급한다. `DUPLICATE`와 `REVIEW_REQUIRED`에는 발급하지 않는다.

### 6.2 상태와 원자성

Token의 처리 상태는 최소 `ISSUED`, `CREATED`, `DUPLICATE`를 구분한다. 만료는 `expires_at`으로 판정한다.

생성 확정 Application 유스케이스는 PostgreSQL 트랜잭션 안에서 Token 행을 잠그고 다음 순서로 처리한다.

1. 제출 Token을 SHA-256으로 해시해 레코드를 조회한다.
2. 관리자·자원 종류와 현재 상태를 검증한다. `ISSUED`일 때만 만료를 검사하고 완료 상태는 24시간 재현 기간 동안 기존 결과를 우선한다.
3. `ISSUED`이면 외부 동일성 기준으로 중복을 다시 확인한다.
4. 중복이 아니면 저장된 후보 Snapshot으로 Entity를 생성하고 Token을 `CREATED`와 결과 ID로 갱신한다.
5. 생성 INSERT는 해당 외부 동일성 고유 제약에 대해 `ON CONFLICT DO NOTHING RETURNING`을 사용한다. 반환 행이 없으면 기존 자원을 조회하고 Token을 `DUPLICATE`와 기존 자원 ID로 갱신한다.
6. Entity 저장과 Token 상태 갱신을 함께 커밋한다.

예상하지 못한 저장 오류로 트랜잭션이 rollback되면 Token도 `ISSUED`로 남아 유효기간 안에 안전하게 재시도할 수 있어야 한다. 이 제한된 `ON CONFLICT DO NOTHING`은 고유성 충돌로 PostgreSQL 트랜잭션 전체가 실패 상태가 되는 것을 피하고 같은 트랜잭션에서 `DUPLICATE` 결과를 기록하기 위한 것이며, 일반 저장 로직의 광범위한 upsert 도입을 뜻하지 않는다.

### 6.3 재시도 결과

- `ISSUED`의 최초 생성 성공: `201 Created`
- `CREATED`인 동일 관리자·동일 Token: 저장된 결과 ID로 기존 Entity를 조회해 `200 OK`
- `DUPLICATE`인 동일 관리자·동일 Token: 같은 `409 DUPLICATE_*`와 같은 기존 자원 정보
- 사용되지 않았지만 만료된 Token: `409 VERIFICATION_EXPIRED`
- 존재하지 않는 해시, 다른 관리자 또는 다른 자원 생성 API에 제출된 Token: `400 INVALID_CONFIRMATION_TOKEN`

생성 확정 요청은 Token만 받으므로 별도 요청 fingerprint를 두지 않는다. Token에 저장된 자원 종류가 요청 API와 다르면 잘못된 Token으로 처리한다.

### 6.4 보관과 정리

- 미사용 Token의 유효기간은 발급 후 10분이다.
- `CREATED`·`DUPLICATE` 완료 레코드는 `completed_at`부터 24시간 보관한다.
- 미사용 만료 레코드도 만료 오류를 안정적으로 재현할 수 있도록 `expires_at`부터 24시간 보관한다.
- 별도 Scheduler를 추가하지 않고 새 Token 발급 시 보관 기한이 지난 레코드를 제한된 건수로 지연 정리한다.
- 정리 실패는 Token 발급을 실패시키지 않으며 로그와 운영 지표로 남긴다.

## 7. 선택 근거

후보와 Token 상태, 최종 Entity가 모두 PostgreSQL에 있으면 한 로컬 트랜잭션으로 단일 사용과 생성 원자성을 보장할 수 있다. 서명 Token과 Redis를 사용하면 Redis 소비와 PostgreSQL 생성 사이의 이중 저장소 원자성 문제가 생기며, 현재 저빈도 관리자 등록에 그 복잡도를 정당화할 근거가 없다.

고엔트로피 Token의 SHA-256 해시는 DB 유출 시 원문 사용을 막으면서 직접 조회를 가능하게 한다. 완료 결과를 보존하면 응답 유실 뒤 재요청이 두 번째 생성을 일으키지 않고 성공 또는 중복 결과를 결정적으로 재현한다.

## 8. 트레이드오프

무상태 Token보다 PostgreSQL 쓰기와 단기 레코드 정리가 추가된다. 후보 Snapshot을 JSON 형태로 저장하면 자원별 역직렬화와 버전 호환 관리가 필요하다. 완료 재시도 결과를 반환하려면 결과 자원이 24시간 안에 삭제·비공개로 바뀌는 경우의 조회 정책을 구현에서 명시해야 한다.

반면 Redis·메시지 브로커·분산 트랜잭션 없이 현재 데이터베이스의 행 잠금과 로컬 트랜잭션만으로 가장 중요한 원자성을 보장한다.

## 9. 적용 범위

맛집·Creator·Video 검증 미리보기와 생성 확정 API에 적용한다. 관리자 로그인 Refresh Token, 일반 사용자 인증, Visit의 `visitEvidenceConfirmed` 선언에는 적용하지 않는다.

## 10. 강제 규칙

- Token 원문과 후보 Snapshot 전체를 로그·메트릭·오류 응답에 기록하지 않는다.
- Token 조회·관리자 검증·상태 판정·중복 확인·Entity 저장·완료 상태 기록은 Application 유스케이스가 조정한다.
- 외부 API 호출을 Token 소비 트랜잭션 안에서 수행하지 않는다.
- `CREATED` 또는 `DUPLICATE` 상태에서 두 번째 Entity 생성이나 외부 재검증을 수행하지 않는다.
- DB 고유 제약은 Token 행 잠금과 별개로 최종 중복 방어선으로 유지한다.

## 11. 금지 사항

확인 Token의 JWT 재사용, Redis 저장, Token 원문 DB 저장, 후보를 클라이언트 생성 요청에서 다시 신뢰하는 방식, 소비 상태를 Entity 생성과 별도 트랜잭션에서 먼저 커밋하는 방식을 금지한다.

## 12. 구현 및 운영 영향

Flyway로 단기 Token 테이블·고유 해시·후보 스키마 버전·상태·만료 및 정리 인덱스를 추가한다. Application에는 Token 발급 Port와 원자적 소비 흐름이 필요하며, 배포 중 이전 버전의 `ISSUED` 후보도 10분 동안 역직렬화할 수 있어야 한다. API는 재시도 `200`과 결정적인 중복 `409`를 문서화한다. 운영 지표에는 발급·만료·생성·중복·재시도·정리 실패 건수를 포함하되 Token 값은 태그로 사용하지 않는다.

## 13. 검증 방법

- 정상 Token 최초 사용이 Entity 한 건과 `CREATED` 결과를 같은 트랜잭션으로 커밋하는지 검증한다.
- 같은 Token을 동시에 두 번 제출해 Entity는 한 건만 생성되고 한 요청은 `201`, 다른 요청은 동일 Entity의 `200`을 받는지 검증한다.
- 응답 직전 장애를 주입한 뒤 같은 Token 재시도가 기존 Entity의 `200`을 반환하는지 검증한다.
- 다른 요청이 먼저 같은 외부 자원을 생성한 경우 최초·재시도 모두 같은 `409 DUPLICATE_*`와 기존 ID를 반환하는지 검증한다.
- 생성 저장 실패 시 Token이 `ISSUED`로 남고 유효기간 안에 재시도할 수 있는지 검증한다.
- 만료·변조·다른 관리자·잘못된 자원 API와 Token 원문 로그 미노출을 검증한다.
- 보관 기한 전 결과 재현과 기한 후 제한 정리를 검증한다.

## 14. 재검토 조건

관리자 등록량이 PostgreSQL 단기 상태 부하를 유발하거나, 여러 서비스가 독립 데이터베이스에서 같은 Token을 소비하거나, 생성 뒤 비동기 워크플로까지 원자적으로 연결해야 할 때 재검토한다. 이 경우 별도 멱등성 저장소나 Transactional Outbox를 검토하되 단일 사용과 결과 재현 계약은 유지한다.

## 15. 관련 문서

- [관리자 기준정보 등록 API](../../05-specs/api/admin/reference-data-api.md)
- [공통 오류 계약](../../05-specs/api/common/error-contract.md)
- [트랜잭션 경계](../../06-architecture/transaction-boundaries.md)
- [통합 계정 인증 ADR](auth-007-unified-account-rbac-session.md)
- [PostgreSQL ADR](../data/data-001-postgresql.md)
