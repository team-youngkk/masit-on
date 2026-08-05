---
id: DATA-E2-001
title: 2차 확장 데이터 계약
status: approved
related_documents:
  - README.md
  - ../../01-requirements/functional-requirements.md
  - ../../01-requirements/business-rules.md
  - ../api/common/second-expansion-contract.md
  - ../api/personal/personal-collection-api.md
  - ../api/discovery/popular-restaurant-api.md
  - ../api/curation/curation-api.md
  - ../api/participation/submission-report-api.md
  - ../api/notification/notification-api.md
  - ../../02-analysis/second-expansion-domain-boundaries.md
  - ../../02-analysis/second-expansion-workstreams.md
  - migration-plan.md
  - ../../07-adr/data/data-009-pre-release-migration-consolidation.md
  - ../../07-adr/data/data-011-popular-restaurant-request-time-aggregation.md
  - ../../07-adr/data/data-012-second-expansion-retention-cleanup.md
  - ../../07-adr/integration/notify-002-in-app-notification-reliability.md
---

# 2차 확장 데이터 계약

## 1. 결정 요약

| 논리 개념 | 물리 저장 | 소유 | 결정 |
|---|---|---|---|
| Collection | `personal_collection` | WS-09 | 회원별 비공개 자원, 물리 삭제 |
| CollectionRestaurant | `collection_restaurant` | WS-09 | 순서 열 없이 추가 시각 저장 |
| PopularityMetric / Snapshot | 저장하지 않음 | WS-10 | `favorite` 현재 행을 요청 시 집계 |
| Curation | `curation` | WS-11 | `DRAFT/PUBLISHED`, 메인 순서 1~5 |
| CurationRestaurant | `curation_restaurant` | WS-11 | 관리자 표시 순서 저장 |
| Submission | `submission` | WS-12 | 신규 후보 JSON과 정규화 지문 저장 |
| Report | `report` | WS-12 | 기존 대상 식별자와 신고 유형 저장 |
| ModerationHistory | `moderation_history` | WS-12 | 제보 또는 신고 FK 하나와 관리자 상태 이력 |
| Notification | `notification` | WS-13 | 요청·상태별 한 건, 읽음 시각 저장 |
| NotificationPreference | 저장하지 않음 | 해당 없음 | 처리 결과 알림은 별도 동의·해지 없음 |
| DeviceToken | 저장하지 않음 | 해당 없음 | 푸시·FCM 제외 |
| IdempotencyRecord | `idempotency_record` | 공통 인증/플랫폼 | 2차 확장 생성 API의 24시간 기술 기록 |

Popularity 테이블, 알림 Outbox·전달 작업, 추천 점수와 수신 설정을 만들지 않는다. 파생 순위와 `unreadCount`도 저장하지 않고 현재 행에서 계산한다.

## 2. 공통 물리 규칙

- 내부 ID는 [ADR-DATA-007](../../07-adr/data/data-007-uuid-v4-identifiers.md)에 따른 애플리케이션 생성 `uuid`다.
- 모든 시각은 `timestamp(6) with time zone`, 이름은 단수 `lower_snake_case`를 사용한다.
- 일반 상태·소유 자원에는 논리 삭제 열을 일괄 추가하지 않는다. 요구사항에 정의된 보존과 물리 삭제·식별 제거를 각각 적용한다.
- FK 대상 회원은 `member_account`, 관리자는 `admin_account`, 맛집은 `restaurant`, 찜은 기존 `favorite`다.
- API의 다형 대상은 허용 유형과 실제 행 존재를 애플리케이션에서 검증한다. 하나의 `target_id`에 여러 테이블 FK를 걸지 않는다.

## 3. 컬렉션

### 3.1 `personal_collection`

| 컬럼 | SQL 타입 | Null | 키·제약 | 설명 |
|---|---|---:|---|---|
| `id` | `uuid` | NN | PK | 컬렉션 ID |
| `member_id` | `uuid` | NN | FK → `member_account.id` `ON DELETE CASCADE` | 소유 회원 |
| `name` | `varchar(50)` | NN | trim 1~50 | 중복 이름 허용 |
| `created_at` | 시간 | NN | 기본 현재 시각 | 생성 시각 |
| `updated_at` | 시간 | NN | 기본 현재 시각 | 이름·구성 변경 시각 |

회원당 20개 상한은 생성 트랜잭션에서 `member_account` 행을 잠그고 개수를 확인한다. 목록은 `updated_at DESC, id ASC`다. 삭제와 회원 탈퇴는 행과 구성 관계를 물리 삭제하며 맛집·찜을 변경하지 않는다.

### 3.2 `collection_restaurant`

| 컬럼 | SQL 타입 | Null | 키·제약 | 설명 |
|---|---|---:|---|---|
| `collection_id` | `uuid` | NN | PK 일부, FK → `personal_collection.id` `ON DELETE CASCADE` | 컬렉션 |
| `restaurant_id` | `uuid` | NN | PK 일부, FK → `restaurant.id` `ON DELETE RESTRICT` | 포함 맛집 |
| `added_at` | 시간 | NN | 기본 현재 시각 | 최초 추가 시각 |

복합 PK가 중복 추가를 막는다. 컬렉션당 100개 상한은 컬렉션 행 잠금 뒤 검사한다. 직접 순서 열은 두지 않고 `added_at DESC, restaurant_id ASC`로 조회한다. 맛집 비공개 시 관계를 보존하고 조회에서 숨기며, 맛집 물리 삭제 명령은 관계를 먼저 정리한다.

### 3.3 인덱스·동시성

| 이름 | 정의 | 목적 |
|---|---|---|
| `ix_personal_collection__member_updated` | `(member_id, updated_at DESC, id)` | 내 컬렉션 목록 |
| `ix_collection_restaurant__collection_added` | `(collection_id, added_at DESC, restaurant_id)` | 컬렉션 맛집 목록 |
| `ix_collection_restaurant__restaurant` | `(restaurant_id, collection_id)` | 맛집 삭제 영향 조회 |

중복 PK 충돌은 현재 관계 반환으로 수렴한다. 제거·삭제는 없는 행에도 성공하며 상한 검사와 삽입은 같은 트랜잭션이다.

## 4. 인기 맛집

`PopularityMetric`과 `PopularitySnapshot`은 만들지 않는다. 공개·활성 Restaurant와 기존 `favorite`를 조인해 `COUNT(*)`, `restaurant_id ASC`로 상위 20개를 계산한다.

구조 선택의 근거와 재검토 조건은 [ADR-DATA-011](../../07-adr/data/data-011-popular-restaurant-request-time-aggregation.md)을 따른다.

| 이름 | 정의 | 목적 |
|---|---|---|
| `ix_favorite__restaurant_member` | `(restaurant_id, member_id)` | 현재 찜 수 집계와 맛집 영향 조회 |

찜 추가·해제·회원 탈퇴 트랜잭션 커밋 뒤 다음 조회에 반영한다. 저장소 장애 시 이전 순위를 반환할 Snapshot이 없으므로 조회가 실패한다. 별도 생명주기·삭제·재계산·감사 이력은 없다.

## 5. 큐레이션

### 5.1 `curation`

| 컬럼 | SQL 타입 | Null | 키·제약 | 설명 |
|---|---|---:|---|---|
| `id` | `uuid` | NN | PK | 큐레이션 ID |
| `title` | `varchar(100)` | NN | trim 1~100 | 제목 |
| `description` | `varchar(1000)` | NN | 기본 `''`, 최대 1000 | 설명 |
| `publication_status` | `varchar(16)` | NN | `DRAFT/PUBLISHED` | 게시 상태 |
| `main_position` | `smallint` | Yes | `PUBLISHED`일 때 1~5, `DRAFT`일 때 NULL | 메인 표시 순서 |
| `created_by` | `uuid` | NN | FK → `admin_account.id` `ON DELETE RESTRICT` | 생성 관리자 |
| `updated_by` | `uuid` | NN | FK → `admin_account.id` `ON DELETE RESTRICT` | 마지막 변경 관리자 |
| `created_at` | 시간 | NN | 기본 현재 시각 | 생성 시각 |
| `updated_at` | 시간 | NN | 기본 현재 시각 | 변경 시각 |
| `published_at` | 시간 | Yes | `PUBLISHED`이면 필수 | 마지막 게시 시각, 게시 중단 뒤에도 보존 |

`UNIQUE(publication_status, main_position) DEFERRABLE`과 상태 조합 CHECK로 동시에 게시 가능한 슬롯을 5개로 제한한다. `DRAFT`의 `main_position`은 NULL이고 PostgreSQL unique의 NULL 비동등성을 사용한다. 순서 전체 교체는 트랜잭션에서 제약을 지연해 위치 교환 충돌을 피한다. 삭제 API와 논리 삭제 열은 없다. `DRAFT`는 관리자만 접근하고 `PUBLISHED`만 공개한다.

### 5.2 `curation_restaurant`

| 컬럼 | SQL 타입 | Null | 키·제약 | 설명 |
|---|---|---:|---|---|
| `curation_id` | `uuid` | NN | PK 일부, FK → `curation.id` `ON DELETE CASCADE` | 큐레이션 |
| `restaurant_id` | `uuid` | NN | PK 일부, FK → `restaurant.id` `ON DELETE RESTRICT` | 구성 맛집 |
| `position` | `smallint` | NN | 1~20, `UNIQUE(curation_id, position)` | 표시 순서 |
| `added_at` | 시간 | NN | 기본 현재 시각 | 구성 추가 시각 |

구성은 0~20개이며 전체 배열을 한 트랜잭션에서 교체한다. 공개·활성 맛집만 새로 연결하지만 연결 뒤 상태 변경은 관계를 보존하고 공개 조회에서 숨긴다.

### 5.3 인덱스·동시성·감사

| 이름 | 정의 | 목적 |
|---|---|---|
| `uq_curation__status_main_position` | `UNIQUE(publication_status, main_position) DEFERRABLE` | 메인 최대 5개·순서 고유성·원자 교체 |
| `ix_curation__admin_updated` | `(publication_status, updated_at DESC, id)` | 관리자 목록 |
| `ix_curation_restaurant__restaurant` | `(restaurant_id, curation_id)` | 맛집 상태 변경 경고·삭제 영향 |

편집·구성 교체·게시·순서 변경은 대상 큐레이션 행을 잠그며 메인 순서 교체는 게시 행 전체를 ID 순으로 잠근다. 관리자 변경은 공통 `OPERATION_AUDIT` 로그에 행위자·대상·변경 종류·이전/이후 값 또는 안전한 변경 메타데이터·요청에 사유가 있는 경우 그 사유·traceId를 기록하고 별도 CurationHistory 테이블은 만들지 않는다. 큐레이션 제목·설명 원문은 로그에 남기지 않고 이전/이후 길이 구간만 기록한다. 구성과 메인 순서는 식별자 순서를, 게시 상태는 상태와 메인 위치를 이전/이후 값으로 기록한다.

## 6. 제보·신고

### 6.1 공통 상태와 필드

`RECEIVED`, `IN_REVIEW`, `ACCEPTED`, `REJECTED`, `COMPLETED`만 허용한다. `REJECTED`, `COMPLETED`는 종료 상태다. 설명은 `text`와 길이 CHECK, URL은 `varchar(2048)`, 사유는 `varchar(1000)`으로 저장한다.

### 6.2 `submission`

| 컬럼 | SQL 타입 | Null | 키·제약 | 설명 |
|---|---|---:|---|---|
| `id` | `uuid` | NN | PK | 제보 ID |
| `member_id` | `uuid` | Yes | FK → `member_account.id` `ON DELETE SET NULL` | 탈퇴·보존 만료 시 식별 제거 |
| `target_type` | `varchar(32)` | NN | `RESTAURANT/CREATOR/VIDEO/VISIT_RELATIONSHIP` | 신규 후보 유형 |
| `candidate` | `jsonb` | NN | JSON object | 타입별 API 후보 Snapshot |
| `target_fingerprint` | `bytea` | NN | 정확히 32 byte | 서버 정규화 대상의 SHA-256 지문 |
| `description` | `text` | NN | trim 10~2000 | 회원 설명 |
| `evidence_url` | `varchar(2048)` | Yes | HTTPS 또는 NULL | 선택 근거 URL |
| `status` | `varchar(16)` | NN | 상태 CHECK | 현재 상태 |
| `member_reason` | `varchar(1000)` | Yes | 종료 상태 규칙 | 회원 공개 사유 |
| `internal_note` | `text` | Yes | 관리자 전용 | 검토 기록 |
| `result_action_type` | `varchar(16)` | Yes | `CREATED/UPDATED/HIDDEN` | 완료 조치 |
| `result_target_type` | `varchar(32)` | Yes | 대상 유형 | 완료 자원 유형 |
| `result_target_id` | `uuid` | Yes | 다형 참조 | 완료 자원 ID |
| `created_at`, `updated_at` | 시간 | NN | 기본 현재 시각 | 생성·변경 시각 |
| `terminal_at` | 시간 | Yes | 종료 상태와 조합 | 종료 시각 |
| `member_unlinked_at` | 시간 | Yes | `member_id`와 조합 | 회원 식별 제거 시각 |

열린 상태에서 `(member_id, target_type, target_fingerprint)` partial unique index로 같은 제보를 막는다.

### 6.3 `report`

`submission`의 소유·상태·사유·결과·시각 필드를 동일하게 가지며 후보 대신 다음 필드를 둔다.

| 컬럼 | SQL 타입 | Null | 키·제약 | 설명 |
|---|---|---:|---|---|
| `target_type` | `varchar(32)` | NN | 허용 대상 CHECK | 기존 대상 유형 |
| `target_id` | `uuid` | NN | 다형 참조 | 기존 대상 ID |
| `report_type` | `varchar(32)` | NN | API 허용값 CHECK | 오류·폐업 등 신고 유형 |

열린 상태에서 `(member_id, target_type, target_id, report_type)` partial unique index로 중복 신고를 막는다. 다형 대상 존재와 유형 조합은 접수 트랜잭션에서 검증하며 신고 접수만으로 대상 상태를 변경하지 않는다.

### 6.4 `moderation_history`

| 컬럼 | SQL 타입 | Null | 키·제약 | 설명 |
|---|---|---:|---|---|
| `id` | `uuid` | NN | PK | 이력 ID |
| `submission_id` | `uuid` | Yes | FK → `submission.id` `ON DELETE CASCADE` | 제보 이력 |
| `report_id` | `uuid` | Yes | FK → `report.id` `ON DELETE CASCADE` | 신고 이력 |
| `admin_account_id` | `uuid` | NN | FK → `admin_account.id` `ON DELETE RESTRICT` | 처리 관리자 |
| `from_status` | `varchar(16)` | NN | 상태 CHECK | 이전 상태 |
| `to_status` | `varchar(16)` | NN | 상태 CHECK | 새 상태 |
| `member_reason` | `varchar(1000)` | Yes |  | 당시 공개 사유 Snapshot |
| `internal_note` | `text` | Yes |  | 당시 내부 기록 Snapshot |
| `result_action_type`, `result_target_type`, `result_target_id` | 상태별 타입 | Yes | 완료 상태와 조합 | 실제 조치 Snapshot |
| `trace_id` | `varchar(64)` | NN | 빈 값 금지 | 요청 추적 ID |
| `created_at` | 시간 | NN | 기본 현재 시각 | 처리 시각 |

DB CHECK `ck_moderation_history__exactly_one_request`는 `(submission_id IS NOT NULL) <> (report_id IS NOT NULL)`을 강제해 제보·신고 FK 중 정확히 하나만 값이 있게 한다. 요청별 `to_status`는 한 번만 기록한다. 이력에는 회원 ID와 회원 입력 원문을 복제하지 않는다.

ModerationHistory는 관리자 전용 데이터이며 회원·공개 조회에 노출하지 않는다. 요청이 유지되는 동안 함께 보존하고 회원 연결 제거 뒤에도 비식별 감사 근거로 남긴다.

### 6.5 인덱스·일일 제한·동시성

| 이름 | 정의 요약 | 목적 |
|---|---|---|
| `ux_submission__open_member_target` | 회원·유형·지문, 열린 상태 partial unique | 열린 제보 중복 차단 |
| `ux_report__open_member_target_type` | 회원·대상·신고 유형, 열린 상태 partial unique | 열린 신고 중복 차단 |
| `ix_submission__member_created` / `ix_report__member_created` | `(member_id, created_at DESC, id)` | 본인 목록·일일 합산 |
| `ix_submission__admin_queue` / `ix_report__admin_queue` | `(status, created_at, id)` | 오래된 관리자 검토 큐 |
| `ix_submission__unlink_terminal` / `ix_report__unlink_terminal` | `(terminal_at) WHERE member_id IS NOT NULL AND terminal_at IS NOT NULL` | 종료 1년 식별 제거 |
| `ux_moderation_history__submission_status` | `(submission_id, to_status) WHERE submission_id IS NOT NULL` | 제보 상태 이력 중복 차단 |
| `ux_moderation_history__report_status` | `(report_id, to_status) WHERE report_id IS NOT NULL` | 신고 상태 이력 중복 차단 |

접수는 `member_account` 행을 잠그고 Asia/Seoul 당일 두 테이블 합계를 검사한 뒤 삽입해 합산 5건을 동시 요청에서도 지킨다. 상태 전이는 현재 상태 조건부 갱신 또는 요청 행 잠금으로 직렬화하고 이력·알림을 같은 트랜잭션에 저장한다.

회원 탈퇴 Command는 계정 물리 삭제 전에 제보·신고의 `member_id=NULL`, `member_unlinked_at=now`를 명시적으로 갱신한다. 미종결 요청은 계속 처리하되 알림을 생성하지 않는다. 종료 요청은 `terminal_at + 1년`에 같은 두 필드를 갱신한다. 요청 본문과 비식별 감사 이력의 추가 삭제 기한은 별도 법적·운영 정책 승인 전까지 자동 purge하지 않는다.

## 7. 사용자 알림

### 7.1 `notification`

| 컬럼 | SQL 타입 | Null | 키·제약 | 설명 |
|---|---|---:|---|---|
| `id` | `uuid` | NN | PK | 알림 ID |
| `member_id` | `uuid` | NN | FK → `member_account.id` `ON DELETE CASCADE` | 수신 회원 |
| `submission_id` | `uuid` | Yes | FK → `submission.id` `ON DELETE CASCADE` | 제보 알림 |
| `report_id` | `uuid` | Yes | FK → `report.id` `ON DELETE CASCADE` | 신고 알림 |
| `status` | `varchar(16)` | NN | 알림 대상 네 상태 | 상태 Snapshot |
| `title` | `varchar(100)` | NN | 빈 값 금지 | 안전한 표시 제목 |
| `message` | `varchar(500)` | NN | 빈 값 금지 | 회원 원문을 복제하지 않는 표시문 |
| `read_at` | 시간 | Yes |  | NULL이면 미읽음 |
| `created_at` | 시간 | NN | 기본 현재 시각 | 생성 시각 |

DB CHECK `ck_notification__exactly_one_request`는 `(submission_id IS NOT NULL) <> (report_id IS NOT NULL)`을 강제해 제보·신고 FK 중 정확히 하나만 값이 있게 한다. 요청의 회원과 `member_id`가 일치해야 하지만 DB FK만으로 교차 행 소유자 일치를 강제할 수 없으므로 같은 상태 전이 Application Service가 검증한다.

알림은 회원 본인 전용이며 별도 공개 상태와 삭제 API를 두지 않는다.

| 이름 | 정의 | 목적 |
|---|---|---|
| `ux_notification__submission_status` | `(submission_id, status) WHERE submission_id IS NOT NULL` | 요청·상태 중복 생성 방지 |
| `ux_notification__report_status` | `(report_id, status) WHERE report_id IS NOT NULL` | 요청·상태 중복 생성 방지 |
| `ix_notification__member_created` | `(member_id, created_at DESC, id)` | 최신 목록·최근 200개 |
| `ix_notification__member_unread` | `(member_id, created_at DESC, id) WHERE read_at IS NULL` | 정확한 미읽음 수·전체 읽음 |
| `ix_notification__cleanup_created` | `(created_at, member_id)` | 보존 cleanup 후보 |

개별 읽음은 `read_at IS NULL` 조건부 갱신, 전체 읽음은 요청 시작 시각 이전 미읽음을 한 번에 갱신한다. 최초 `read_at`을 보존한다. 탈퇴 시 CASCADE로 삭제한다. 보존은 생성 90일 이내이거나 회원별 최신 200개인 알림을 유지하고, 두 조건을 모두 벗어난 행만 주기 작업이 물리 삭제한다.

NotificationPreference·DeviceToken·Outbox·전송 상태·재시도 열은 만들지 않는다.

상태 전이와 알림 저장의 신뢰성 경계는 [ADR-NOTIFY-002](../../07-adr/integration/notify-002-in-app-notification-reliability.md)를 따른다.

## 8. 생성 멱등성 기술 기록

### 8.1 `idempotency_record`

| 컬럼 | SQL 타입 | Null | 키·제약 | 설명 |
|---|---|---:|---|---|
| `id` | `uuid` | NN | PK | 기술 행 ID |
| `actor_type` | `varchar(16)` | NN | `MEMBER/ADMIN` | 호출 주체 종류 |
| `actor_id` | `uuid` | NN | 다형 참조 | 회원 또는 관리자 ID |
| `api_scope` | `varchar(64)` | NN | 허용 scope | 생성 API 구분 |
| `key_hash` | `bytea` | NN | 정확히 32 byte | 원문 키 SHA-256 |
| `request_hash` | `bytea` | NN | 정확히 32 byte | 정규화 요청 본문 해시 |
| `response_status` | `smallint` | NN | `201` | 성공 상태 |
| `response_body` | `jsonb` | NN | JSON object | 최초 생성 응답 Snapshot |
| `resource_id` | `uuid` | NN | 다형 참조 | 생성 자원 ID |
| `created_at`, `expires_at` | 시간 | NN | 만료가 생성 이후 | 24시간 보존 |

`UNIQUE(actor_type, actor_id, api_scope, key_hash)`가 성공 기록을 고유하게 한다. 원문 키·Access Token·비밀번호를 저장하지 않는다. 자원 생성과 기록을 한 트랜잭션에서 커밋한다.

`expires_at <= now()`인 행은 cleanup 실행 여부와 무관하게 조회 시 만료로 판정하며 기존 응답을 재생하지 않는다. 동일 키 범위를 직렬화한 트랜잭션에서 만료 행을 삭제하고 새 자원과 새 멱등 기록을 원자적으로 생성한다. 행이 없는 상태에서 동시 삽입해 고유 제약 충돌이 발생하면 승자 행을 다시 읽어 같은 `request_hash`는 새 성공 응답을 재생하고 다른 해시는 `409 IDEMPOTENCY_KEY_REUSED`로 처리한다. 주기 cleanup은 만료 행의 물리 공간 회수만 담당하므로 24시간 의미 경계는 cleanup 주기와 독립적이다. 인덱스 `ix_idempotency_record__expires(expires_at)`를 둔다.

## 9. 삭제·보존·감사 요약

| 데이터 | 삭제·보존 | 회원 탈퇴 | 감사 |
|---|---|---|---|
| 개인 컬렉션·관계 | 사용자가 물리 삭제, 별도 보존 없음 | CASCADE 삭제 | 도메인 이력 없음, trace 로그만 사용 |
| 인기 순위 | 비저장 | 현재 Favorite 삭제 결과 즉시 반영 | 없음 |
| 큐레이션·구성 | 삭제 API 없음, Draft/Published 유지 | 영향 없음 | 관리자 변경 공통 감사 로그 |
| 제보·신고 | 종료 1년 뒤 회원 연결 제거, 본문 자동 purge 없음 | 즉시 회원 연결 제거 | ModerationHistory 영속 보존 |
| 알림 | 90일 또는 최신 200개 중 더 넓은 범위, 그 밖은 삭제 | CASCADE 삭제 | 읽음 변경은 일반 보안 로그, 원문 미기록 |
| 멱등 기록 | 성공 후 24시간 삭제 | actor FK 없음, TTL 삭제 | 기술 추적만 유지 |

### 9.1 보존 작업 운영 계약

| 작업 | 실행 기준 | 책임 | 동시 실행·실패 처리 |
|---|---|---|---|
| 제보·신고 회원 연결 제거 | 매일 04:00 `Asia/Seoul`, 종료 1년 경과 | WS-12 | 초기 단일 인스턴스, 1,000건 단위 commit, 실패 알림 후 다음 실행 재시도 |
| 알림 보존 정리 | 매일 03:30 `Asia/Seoul` | WS-13 | 회원별 최신 200개 계산 후 1,000건 단위 삭제, 목록 조회와 트랜잭션 분리 |
| 멱등 기록 정리 | 매시 15분, 만료 시각 경과 | 공통 인증/플랫폼 | 1,000건 단위 삭제, 실패 시 다음 시간 재시도 |

각 작업은 이미 정리된 행이 없어도 성공하고 중복 실행에도 같은 결과로 수렴하는 멱등 Command다. 현재 단일 애플리케이션 인스턴스에서는 실행 락을 추가하지 않는다. 처리 수·소요 시간·실패 유형을 운영 지표로 남기되 회원 입력 원문과 알림 본문은 로그에 기록하지 않는다.

Scheduler·Batch·락 선택과 재검토 조건은 [ADR-DATA-012](../../07-adr/data/data-012-second-expansion-retention-cleanup.md)를 따른다.

## 10. Flyway 계획

기존 `V1__create_initial_schema.sql`과 `V2__add_expansion_1_schema.sql`은 수정하지 않는다. 다음 비어 있는 버전인 `V3__add_expansion_2_schema.sql` 하나를 새로 계획한다.

1. `personal_collection`, `collection_restaurant`
2. `curation`, `curation_restaurant`
3. `submission`, `report`, `moderation_history`
4. `notification`, `idempotency_record`
5. 기존 `favorite`의 역방향 집계 인덱스와 모든 신규 인덱스

DDL은 위 부모→자식 순서로 한 트랜잭션에서 적용하고 기존 행 backfill과 외부 호출은 없다. 운영 적용 전 파일 통합을 검토하더라도 [ADR-DATA-009](../../07-adr/data/data-009-pre-release-migration-consolidation.md)의 모든 증명을 만족한 경우에만 가능하며, 적용된 V1·V2는 어떤 경우에도 수정하지 않는다.

## 11. 완료 검증

- V2 스키마에서 V3 전진 적용과 빈 DB 전체 적용 결과를 각각 검증한다.
- PK·FK·UK·CHECK, partial unique와 삭제 동작을 PostgreSQL Testcontainers로 검사한다.
- 컬렉션 20/100, 큐레이션 5/20, 제보·신고 합산 5건과 중복을 동시 요청으로 검증한다.
- 상태 전이·ModerationHistory·Notification의 같은 트랜잭션 rollback과 중복 재시도를 실패 주입으로 검사한다.
- 인기·큐레이션·알림 목록의 `EXPLAIN (ANALYZE, BUFFERS)`와 NFR 성능 기준을 확인한다.
- 탈퇴, 종료 1년 식별 제거, 알림 90일/최신 200개, 멱등 기록 24시간 cleanup을 경계 시각으로 검증한다.
