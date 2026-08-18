---
status: accepted
related_documents:
  - constraints.md
  - lifecycle-rules.md
  - physical-data-model.md
  - table-definitions.md
  - second-expansion-data-contract.md
---

# 맛잇온 제약 매핑

## 1. 논리 규칙 → 물리 제약

| 논리 규칙 | 물리 구현 | 애플리케이션 보완 | 오류 |
|---|---|---|---|
| Restaurant 동일 Kakao 장소 금지 | `uk_restaurant__kakao_place_id` | Kakao 응답에서 ID 확인, 생성 전 조회 | `DUPLICATE_RESTAURANT` |
| Creator 동일 채널 금지 | `uk_creator__external_channel_id` | YouTube 확인, 생성 전 조회 | `DUPLICATE_CREATOR` |
| Video 동일 원본 금지 | `uk_video__external_video_id` | YouTube 확인, 생성 전 조회 | `DUPLICATE_VIDEO` |
| Visit 세 대상 조합 유일 | `uk_visit__restaurant_creator_video` | 생성 전 조회 | `DUPLICATE_VISIT_RELATIONSHIP` |
| Region·Category 표준값 유일 | 각 `code`, `name`, `sort_order` UK | Flyway seed 검증 | 기준 데이터 배포 실패 |
| 통합 계정 이메일 유일 | `uk_member_account__email` | 정규화 이메일, 역할 입력과 무관 | 가입·운영 발급 거부 |
| 계정 역할 허용값 | `ck_member_account__role` | 공개 가입은 `MEMBER`; `ADMIN`은 승인 운영 절차 | 잘못된 역할 거부 |
| Video.Creator와 게시 채널 일치 | `fk_video__creator_channel` 복합 FK | Video 연결 전 외부 ID 비교 | `VIDEO_CHANNEL_MISMATCH` |
| Visit.Creator와 Video.Creator 일치 | `fk_visit__video_creator` 복합 FK | null Video.Creator를 같은 트랜잭션에서 연결 | `VIDEO_CHANNEL_MISMATCH` |
| 참조 존재 | 명명된 FK와 기본 `ON DELETE RESTRICT`; 개인화 관계의 회원 FK는 `ON DELETE CASCADE` | 대상별 조회로 404 구분 | `*_NOT_FOUND` |
| 공개 참조만 Visit 생성 | DB FK로는 미보장 | 잠금된 트랜잭션 조회 | `REFERENCE_NOT_PUBLIC` |
| 실제 방문 확인 | DB 저장 대상 아님 | `visitEvidenceConfirmed=true` 필수 | `VISIT_EVIDENCE_INSUFFICIENT` |
| Token 단일 사용 | token hash UK + 행 잠금 + 상태 CHECK | 관리자·자원 종류·만료 검증 | Token 계약 오류 |

## 2. PK·유일 제약 목록

| 이름 | 테이블 | 컬럼 |
|---|---|---|
| `pk_region` | `region` | `id` |
| `uk_region__code` | `region` | `code` |
| `uk_region__name` | `region` | `name` |
| `uk_region__sort_order` | `region` | `sort_order` |
| `pk_food_category` | `food_category` | `id` |
| `uk_food_category__code` | `food_category` | `code` |
| `uk_food_category__name` | `food_category` | `name` |
| `uk_food_category__sort_order` | `food_category` | `sort_order` |
| `pk_admin_account` (legacy, 계약 단계 제거) | `admin_account` | `id` |
| `uk_admin_account__login_id` (legacy, 계약 단계 제거) | `admin_account` | `login_id` |
| `pk_admin_account_migration_map` (전환 staging, 계약 단계 제거) | `admin_account_migration_map` | `admin_account_id` |
| `uk_admin_account_migration_map__normalized_email` (전환 staging, 계약 단계 제거) | `admin_account_migration_map` | `normalized_email` |
| `uk_admin_account_migration_map__member_account_id` (전환 staging, 계약 단계 제거) | `admin_account_migration_map` | `member_account_id` (NULL 제외) |
| `pk_member_account` | `member_account` | `id` |
| `uk_member_account__email` | `member_account` | `email` |
| `pk_restaurant` | `restaurant` | `id` |
| `uk_restaurant__kakao_place_id` | `restaurant` | `kakao_place_id` |
| `pk_creator` | `creator` | `id` |
| `uk_creator__external_channel_id` | `creator` | `external_channel_id` |
| `uk_creator__id_external_channel_id` | `creator` | `id, external_channel_id` |
| `pk_video` | `video` | `id` |
| `uk_video__external_video_id` | `video` | `external_video_id` |
| `uk_video__id_creator_id` | `video` | `id, creator_id` |
| `pk_visit` | `visit` | `id` |
| `uk_visit__restaurant_creator_video` | `visit` | `restaurant_id, creator_id, video_id` |
| `pk_confirmation_token` | `confirmation_token` | `id` |
| `uk_confirmation_token__token_hash` | `confirmation_token` | `token_hash` |
| `pk_favorite` | `favorite` | `member_id, restaurant_id` |
| `pk_recent_restaurant_view` | `recent_restaurant_view` | `member_id, restaurant_id` |

PostgreSQL의 `UNIQUE`는 이미 동일 컬럼 B-tree 인덱스를 만든다. 같은 컬럼의 일반 인덱스를 중복 생성하지 않는다.

## 3. FK 목록과 삭제 정책

| 이름 | 자식 컬럼 | 부모 키 | 삭제·수정 |
|---|---|---|---|
| `fk_restaurant__region` | `restaurant.region_id` | `region.id` | `ON DELETE RESTRICT ON UPDATE RESTRICT` |
| `fk_restaurant__food_category` | `restaurant.food_category_id` | `food_category.id` | 동일 |
| `fk_video__creator_channel` | `video(creator_id, publisher_external_channel_id)` | `creator(id, external_channel_id)` | 동일 |
| `fk_visit__restaurant` | `visit.restaurant_id` | `restaurant.id` | 동일 |
| `fk_visit__creator` | `visit.creator_id` | `creator.id` | 동일 |
| `fk_visit__video_creator` | `visit(video_id, creator_id)` | `video(id, creator_id)` | 동일 |
| `fk_confirmation_token__admin_account` (legacy) | `confirmation_token.admin_account_id` | `admin_account.id` | 확장 단계 유지, 계약 단계 제거 |
| `fk_confirmation_token__member_account` (target) | `confirmation_token.admin_account_id` 또는 후속 rename 컬럼 | `member_account.id` | `ON DELETE RESTRICT ON UPDATE RESTRICT` |
| `fk_admin_account_migration_map__admin_account` (전환 staging) | `admin_account_migration_map.admin_account_id` | `admin_account.id` | `ON DELETE RESTRICT ON UPDATE RESTRICT`, 계약 단계 제거 |
| `fk_admin_account_migration_map__member_account` (전환 staging) | `admin_account_migration_map.member_account_id` | `member_account.id` | `ON DELETE RESTRICT ON UPDATE RESTRICT`, 계약 단계 제거 |
| `fk_favorite__member_account` | `favorite.member_id` | `member_account.id` | `ON DELETE CASCADE ON UPDATE RESTRICT` |
| `fk_favorite__restaurant` | `favorite.restaurant_id` | `restaurant.id` | `ON DELETE RESTRICT ON UPDATE RESTRICT` |
| `fk_recent_restaurant_view__member_account` | `recent_restaurant_view.member_id` | `member_account.id` | `ON DELETE CASCADE ON UPDATE RESTRICT` |
| `fk_recent_restaurant_view__restaurant` | `recent_restaurant_view.restaurant_id` | `restaurant.id` | `ON DELETE RESTRICT ON UPDATE RESTRICT` |

모든 FK는 기본 immediate 검사다. `DEFERRABLE`은 사용하지 않는다. Video.Creator 연결을 먼저 수행한 뒤 Visit를 INSERT하면 지연 제약 없이 원자성을 만족한다.

## 4. CHECK 제약

반복되는 이름에서 `{table}`은 `restaurant`, `creator`, `video`, `visit` 중 해당 테이블이다.

| 이름 | 식 |
|---|---|
| `ck_region__sort_order` | `sort_order BETWEEN 1 AND 25` |
| `ck_food_category__sort_order` | `sort_order BETWEEN 1 AND 10` |
| `ck_admin_account__login_id_not_blank` (legacy) | `btrim(login_id) <> ''` |
| `ck_admin_account__role` (legacy) | `role = 'ADMIN'` |
| `ck_admin_account_migration_map__email` (전환 staging) | `normalized_email = lower(btrim(normalized_email))`이고 회원가입 이메일 형식 충족 |
| `ck_admin_account_migration_map__approval_not_blank` (전환 staging) | `btrim(approval_record_id) <> ''` |
| `ck_member_account__role` | `role IN ('MEMBER','ADMIN')` |
| `ck_restaurant__phone_number` | `char_length(phone_number) BETWEEN 7 AND 20 AND phone_number ~ '^[0-9 +()\\-]+$'` |
| `ck_restaurant__coordinate_pair` | `(latitude IS NULL AND longitude IS NULL) OR (latitude IS NOT NULL AND longitude IS NOT NULL)` |
| `ck_restaurant__coordinate_range` | `(latitude BETWEEN -90 AND 90) AND (longitude BETWEEN -180 AND 180)` when both values are present |
| `ck_{table}__publication_status` | `publication_status IN ('PUBLIC','PRIVATE')` |
| `ck_{table}__lifecycle_status` | `lifecycle_status IN ('ACTIVE','DELETED')` |
| `ck_creator__external_availability_status` | `external_availability_status IN ('AVAILABLE','UNAVAILABLE')` |
| `ck_video__external_availability_status` | 위와 동일 |
| `ck_creator__external_unavailable_private` | `external_availability_status='AVAILABLE' OR publication_status='PRIVATE'` |
| `ck_video__external_unavailable_private` | 위와 동일 |
| `ck_{table}__deleted_pair` | `(lifecycle_status='ACTIVE' AND deleted_at IS NULL) OR (lifecycle_status='DELETED' AND deleted_at IS NOT NULL AND publication_status='PRIVATE')` |
| `ck_confirmation_token__token_hash_length` | `octet_length(token_hash)=32` |
| `ck_confirmation_token__resource_type` | `resource_type IN ('RESTAURANT','CREATOR','VIDEO')` |
| `ck_confirmation_token__schema_version` | `candidate_schema_version > 0` |
| `ck_confirmation_token__snapshot_object` | `jsonb_typeof(candidate_snapshot)='object'` |
| `ck_confirmation_token__status` | `status IN ('ISSUED','CREATED','DUPLICATE')` |
| `ck_confirmation_token__expiry` | `expires_at > issued_at` |
| `ck_confirmation_token__completion_pair` | `ISSUED`면 완료 컬럼 둘 다 null, 완료 상태면 둘 다 not null |

필수 문자열에는 테이블별 `btrim(column) <> ''` CHECK를 둔다. nullable 문자열은 `column IS NULL OR btrim(column) <> ''`를 사용한다. URL scheme·host와 서울 주소 판정은 신뢰 가능한 URL parser 및 외부 검증이 필요하므로 DB 정규식으로 흉내 내지 않는다.

## 5. 상태 전환 불변식

| 대상 | 허용 전환 | 한 트랜잭션의 필수 갱신 |
|---|---|---|
| 공개 데이터 | `PUBLIC ↔ PRIVATE` | `updated_at` |
| 삭제 | `ACTIVE → DELETED` | `publication_status=PRIVATE`, `deleted_at=now`, `updated_at=now` |
| 복구 | `DELETED → ACTIVE` | `deleted_at=NULL`, 검증 결과에 따른 publication, `updated_at=now` |
| 외부 이용 불가 | `AVAILABLE → UNAVAILABLE` | `publication_status=PRIVATE`, `external_status_checked_at`, `updated_at` |
| 외부 복구 | `UNAVAILABLE → AVAILABLE` | 재검증 후 publication 결정, 확인·변경 시각 |
| Token 완료 | `ISSUED → CREATED/DUPLICATE` | `completed_at`, `result_resource_id` |
| 계정 역할·상태·비밀번호 변경 | 허용된 운영/회원 흐름 | `updated_at` 갱신과 해당 계정 Redis 세션 전체 폐기 |

완료 Token 상태는 되돌리지 않는다. 핵심 데이터 삭제는 복구할 수 있으므로 유일 제약에서 삭제 행을 제외하지 않는다.

## 6. 동시성·오류 변환

- 고유 제약 위반은 SQLSTATE `23505`, FK 위반은 `23503`, CHECK 위반은 `23514`로 분류한다.
- 애플리케이션은 constraint name을 기준으로 도메인 오류를 변환한다. 오류 문자열 전문 파싱에 의존하지 않는다.
- Visit의 동시 생성은 복합 UK가 최종 승자를 결정한다.
- 확인 Token은 `token_hash` 조회 후 `FOR UPDATE`로 한 요청만 `ISSUED`를 처리한다.
- 단순 선조회 후 INSERT만으로 고유성을 보장했다고 간주하지 않는다.

## 7. DB가 강제하지 않는 규칙

외부 사실, 참조 대상의 현재 공개·활성 상태, `기타` 의미, 실제 방문 장면, 후보 Snapshot 스키마 내용, 결과 자원의 다형 참조는 애플리케이션과 통합 테스트가 보장한다. DB 제약으로 옮길 수 없는 이유가 사라지면 새 마이그레이션으로 강화한다.

## 8. 2차 확장 제약 라우팅

2차 확장의 명명된 PK·FK·UK·CHECK, 애플리케이션 행 잠금과 상태-이력-알림 원자성은 [2차 확장 데이터 계약](second-expansion-data-contract.md)을 따른다. 특히 다형 신고 대상·처리 결과의 존재, 요청과 알림의 회원 일치는 애플리케이션 검증이며, 열린 요청·상태 이력·알림 중복은 partial unique로 최종 강제한다.

- 생성 멱등 기록은 `UNIQUE(actor_type, actor_id, api_scope, key_hash)`로 최종 강제한다. 만료 여부는 조회 시 `expires_at`으로 판정하고 cleanup은 물리 정리에만 사용한다.
- `moderation_history`와 `notification`의 제보·신고 FK XOR는 각각 DB CHECK `ck_moderation_history__exactly_one_request`, `ck_notification__exactly_one_request`로 강제한다.
