---
status: accepted
related_documents:
  - physical-data-model.md
  - constraint-mapping.md
  - index-strategy.md
  - migration-plan.md
---

# 맛잇온 테이블 정의

## 1. 표기

- `NN`: `NOT NULL`
- 기본 ID는 애플리케이션 생성 UUID라 DB `DEFAULT`를 두지 않는다.
- 모든 시간 컬럼은 `timestamp(6) with time zone`이다.
- 실제 DDL에는 이 문서의 PK·FK·UK·CHECK 이름을 명시한다.

## 2. `region`

서울특별시 자치구 기준 데이터다.

| 컬럼 | SQL 타입 | Null | 기본값 | 키·제약 | 설명 |
|---|---|---:|---|---|---|
| `id` | `uuid` | NN | 없음 | PK | 내부 ID |
| `code` | `varchar(32)` | NN | 없음 | UK, 빈 값 금지 | 변경되지 않는 애플리케이션 코드 |
| `name` | `varchar(20)` | NN | 없음 | UK, 빈 값 금지 | API 표준 자치구 이름 |
| `sort_order` | `smallint` | NN | 없음 | UK, `1..25` | 선택 목록 순서 |
| `active` | `boolean` | NN | `true` |  | 신규 Restaurant 연결 허용 |
| `created_at` | 시간 | NN | `CURRENT_TIMESTAMP` |  | 생성 시각 |
| `updated_at` | 시간 | NN | `CURRENT_TIMESTAMP` |  | 변경 시각 |

## 3. `food_category`

대표 음식 카테고리 기준 데이터다.

| 컬럼 | SQL 타입 | Null | 기본값 | 키·제약 | 설명 |
|---|---|---:|---|---|---|
| `id` | `uuid` | NN | 없음 | PK | 내부 ID |
| `code` | `varchar(32)` | NN | 없음 | UK, 빈 값 금지 | 변경되지 않는 애플리케이션 코드 |
| `name` | `varchar(30)` | NN | 없음 | UK, 빈 값 금지 | API 표준 이름 |
| `sort_order` | `smallint` | NN | 없음 | UK, `1..10` | 표시 순서 |
| `active` | `boolean` | NN | `true` |  | 신규 Restaurant 연결 허용 |
| `created_at` | 시간 | NN | `CURRENT_TIMESTAMP` |  | 생성 시각 |
| `updated_at` | 시간 | NN | `CURRENT_TIMESTAMP` |  | 변경 시각 |

## 4. `admin_account`

사전 발급 관리자 계정이다.

| 컬럼 | SQL 타입 | Null | 기본값 | 키·제약 | 설명 |
|---|---|---:|---|---|---|
| `id` | `uuid` | NN | 없음 | PK | 관리자 내부 ID |
| `login_id` | `varchar(100)` | NN | 없음 | UK, 빈 값 금지 | trim 후 1~100자 로그인 ID |
| `password_hash` | `varchar(255)` | NN | 없음 | 빈 값 금지 | BCrypt 등 Spring Security 인코더 결과 |
| `role` | `varchar(16)` | NN | `'ADMIN'` | `ADMIN`만 허용 | MVP 단일 권한 |
| `active` | `boolean` | NN | `true` |  | 로그인·등록 권한 |
| `created_at` | 시간 | NN | `CURRENT_TIMESTAMP` |  | 발급 시각 |
| `updated_at` | 시간 | NN | `CURRENT_TIMESTAMP` |  | 변경 시각 |

로그인 ID의 대소문자 동일성은 API가 별도로 정의하지 않았으므로 저장값의 정확 일치를 사용한다. 비밀번호 원문과 Refresh Token은 이 테이블에 저장하지 않는다.

## 5. `restaurant`

| 컬럼 | SQL 타입 | Null | 기본값 | 키·제약 | 설명 |
|---|---|---:|---|---|---|
| `id` | `uuid` | NN | 없음 | PK | API 식별자 |
| `region_id` | `uuid` | NN | 없음 | FK → `region.id` | 서울 자치구 |
| `food_category_id` | `uuid` | NN | 없음 | FK → `food_category.id` | 대표 카테고리 |
| `name` | `varchar(100)` | NN | 없음 | trim 1~100 | 표시 이름 |
| `kakao_place_id` | `varchar(64)` | NN | 없음 | UK, 빈 값 금지 | 검증된 Kakao 장소 ID |
| `kakao_place_url` | `varchar(2048)` | NN | 없음 | 빈 값 금지 | 검증·정규화된 HTTPS URL |
| `road_address` | `varchar(255)` | NN | 없음 | trim 1~255 | 서울 전체 도로명주소 |
| `detail_address` | `varchar(200)` | Yes | `NULL` | null 또는 빈 값 금지 | 상세 위치 |
| `phone_number` | `varchar(20)` | NN | 없음 | 길이 7~20, 허용 문자 | 확인된 전화번호 |
| `latitude` | `numeric(9,6)` | Yes | `NULL` | `-90..90`, longitude와 null 쌍 | WGS84 위도 |
| `longitude` | `numeric(9,6)` | Yes | `NULL` | `-180..180`, latitude와 null 쌍 | WGS84 경도 |
| `publication_status` | `varchar(16)` | NN | `'PUBLIC'` | `PUBLIC/PRIVATE` | 공개 상태 |
| `lifecycle_status` | `varchar(16)` | NN | `'ACTIVE'` | `ACTIVE/DELETED` | 삭제 상태 |
| `created_at` | 시간 | NN | `CURRENT_TIMESTAMP` |  | 생성 시각 |
| `updated_at` | 시간 | NN | `CURRENT_TIMESTAMP` |  | 변경 시각 |
| `deleted_at` | 시간 | Yes | `NULL` | lifecycle과 쌍 | 삭제 시각 |

`OTHER`도 다른 FoodCategory와 같은 표준 참조 행이며 Restaurant에 별도 보충 이름 컬럼을 두지 않는다. 좌표는 둘 다 값이 있거나 둘 다 `NULL`이어야 하며, 좌표가 없어도 일반 목록·상세 공개 대상에서는 제외되지 않는다.

## 6. `creator`

| 컬럼 | SQL 타입 | Null | 기본값 | 키·제약 | 설명 |
|---|---|---:|---|---|---|
| `id` | `uuid` | NN | 없음 | PK | API 식별자 |
| `external_channel_id` | `varchar(64)` | NN | 없음 | UK, 빈 값 금지 | YouTube 채널 ID |
| `channel_name` | `text` | NN | 없음 | 빈 값 금지 | 현재 표시 이름; 외부 제공자 길이에 임의 상한을 두지 않음 |
| `profile_image_url` | `varchar(2048)` | Yes | `NULL` | null 또는 빈 값 금지 | 공개 상세용 프로필 이미지 URL |
| `description` | `text` | Yes | `NULL` | null 또는 빈 값 금지 | 공개 상세용 채널 소개 |
| `handle` | `varchar(255)` | Yes | `NULL` | null 또는 빈 값 금지 | 공개 상세용 채널 handle |
| `channel_url` | `varchar(2048)` | NN | 없음 | 빈 값 금지 | 정규화된 채널 URL |
| `publication_status` | `varchar(16)` | NN | `'PUBLIC'` | `PUBLIC/PRIVATE` | 공개 상태 |
| `lifecycle_status` | `varchar(16)` | NN | `'ACTIVE'` | `ACTIVE/DELETED` | 삭제 상태 |
| `external_availability_status` | `varchar(16)` | NN | `'AVAILABLE'` | `AVAILABLE/UNAVAILABLE` | YouTube 가용성 |
| `external_status_checked_at` | 시간 | NN | 없음 |  | 미리보기의 외부 확인 시각 |
| `created_at` | 시간 | NN | `CURRENT_TIMESTAMP` |  | 생성 시각 |
| `updated_at` | 시간 | NN | `CURRENT_TIMESTAMP` |  | 변경 시각 |
| `deleted_at` | 시간 | Yes | `NULL` | lifecycle과 쌍 | 삭제 시각 |

`UNIQUE(id, external_channel_id)`를 추가해 Video의 복합 FK 대상 키로 사용한다.

## 7. `video`

| 컬럼 | SQL 타입 | Null | 기본값 | 키·제약 | 설명 |
|---|---|---:|---|---|---|
| `id` | `uuid` | NN | 없음 | PK | API 식별자 |
| `creator_id` | `uuid` | Yes | `NULL` | 복합 FK → Creator | 내부 게시 Creator |
| `external_video_id` | `varchar(32)` | NN | 없음 | UK, 빈 값 금지 | YouTube 영상 ID |
| `publisher_external_channel_id` | `varchar(64)` | NN | 없음 | 복합 FK 구성 | 외부 게시 채널 ID |
| `title` | `text` | NN | 없음 | 빈 값 금지 | 영상 제목; 외부 제공자 길이에 임의 상한을 두지 않음 |
| `source_url` | `varchar(2048)` | NN | 없음 | 빈 값 금지 | 정규화된 원본 URL |
| `thumbnail_url` | `varchar(2048)` | NN | 없음 | 빈 값 금지 | 썸네일 URL |
| `published_at` | 시간 | Yes | `NULL` |  | YouTube 게시 시각 |
| `publication_status` | `varchar(16)` | NN | `'PUBLIC'` | `PUBLIC/PRIVATE` | 공개 상태 |
| `lifecycle_status` | `varchar(16)` | NN | `'ACTIVE'` | `ACTIVE/DELETED` | 삭제 상태 |
| `external_availability_status` | `varchar(16)` | NN | `'AVAILABLE'` | `AVAILABLE/UNAVAILABLE` | 외부 가용성 |
| `external_status_checked_at` | 시간 | NN | 없음 |  | 미리보기의 외부 확인 시각 |
| `created_at` | 시간 | NN | `CURRENT_TIMESTAMP` |  | 생성 시각 |
| `updated_at` | 시간 | NN | `CURRENT_TIMESTAMP` |  | 변경 시각 |
| `deleted_at` | 시간 | Yes | `NULL` | lifecycle과 쌍 | 삭제 시각 |

복합 FK `(creator_id, publisher_external_channel_id)` → `creator(id, external_channel_id)`가 채널 일치를 강제한다. `UNIQUE(id, creator_id)`는 Visit 복합 FK 대상 키다.

## 8. `visit`

| 컬럼 | SQL 타입 | Null | 기본값 | 키·제약 | 설명 |
|---|---|---:|---|---|---|
| `id` | `uuid` | NN | 없음 | PK | 관리자 응답 식별자 |
| `restaurant_id` | `uuid` | NN | 없음 | FK → Restaurant | 방문 맛집 |
| `creator_id` | `uuid` | NN | 없음 | FK → Creator 및 복합 FK | 방문 Creator |
| `video_id` | `uuid` | NN | 없음 | 복합 FK → Video | 근거 영상 |
| `publication_status` | `varchar(16)` | NN | `'PUBLIC'` | `PUBLIC/PRIVATE` | 공개 상태 |
| `lifecycle_status` | `varchar(16)` | NN | `'ACTIVE'` | `ACTIVE/DELETED` | 삭제 상태 |
| `created_at` | 시간 | NN | `CURRENT_TIMESTAMP` |  | 생성 시각 |
| `updated_at` | 시간 | NN | `CURRENT_TIMESTAMP` |  | 변경 시각 |
| `deleted_at` | 시간 | Yes | `NULL` | lifecycle과 쌍 | 삭제 시각 |

`UNIQUE(restaurant_id, creator_id, video_id)`로 논리 삭제 행을 포함한 전체 이력에서 조합 중복을 금지한다. 삭제된 같은 조합을 재등록하려면 새 행을 만들지 않고 검증 후 기존 행을 복구한다.

## 9. `confirmation_token`

| 컬럼 | SQL 타입 | Null | 기본값 | 키·제약 | 설명 |
|---|---|---:|---|---|---|
| `id` | `uuid` | NN | 없음 | PK | 내부 행 ID |
| `token_hash` | `bytea` | NN | 없음 | UK, 정확히 32 byte | SHA-256 해시 |
| `admin_account_id` | `uuid` | NN | 없음 | FK → AdminAccount | 발급 관리자 |
| `resource_type` | `varchar(16)` | NN | 없음 | `RESTAURANT/CREATOR/VIDEO` | 생성 API 결속 |
| `candidate_schema_version` | `smallint` | NN | `1` | `> 0` | Snapshot 역직렬화 버전 |
| `identity_key` | `varchar(128)` | NN | 없음 | 빈 값 금지 | 장소·채널·영상 외부 동일성 값 |
| `candidate_snapshot` | `jsonb` | NN | 없음 | JSON object | 서버 검증 완료 후보 |
| `status` | `varchar(16)` | NN | `'ISSUED'` | `ISSUED/CREATED/DUPLICATE` | 처리 결과 |
| `issued_at` | 시간 | NN | `CURRENT_TIMESTAMP` |  | 발급 시각 |
| `expires_at` | 시간 | NN | 없음 | `issued_at` 이후 | 발급 후 10분 |
| `completed_at` | 시간 | Yes | `NULL` | 상태와 쌍 | 완료 시각 |
| `result_resource_id` | `uuid` | Yes | `NULL` | 상태와 쌍, 다형 참조 | 생성·중복 자원 ID |

`result_resource_id`는 세 테이블 중 하나를 가리키므로 FK를 만들지 않는다. 자원 종류별 완료 처리와 24시간 재현 테스트로 참조 존재를 보장한다.

## 10. `member_account`

회원 계정은 관리자 계정과 identity·권한·인증 수명주기를 분리한다. 이메일은 고유하고, 비밀번호 원문이나 Refresh Token은 저장하지 않는다. 상태는 `PENDING_VERIFICATION`, `ACTIVE`, `DELETION_PENDING`, `DISABLED`이며, 이메일 인증 시각과 삭제 요청 시각의 조합을 CHECK 제약으로 강제한다.

## 11. `member_action_token`

이메일 인증과 비밀번호 재설정은 SHA-256 해시만 저장하는 1회용 Token을 사용한다. Token은 `ISSUED`, `USED`, `REVOKED` 상태와 완료 시각을 가지며, `(member_id, purpose)`별 `ISSUED` Token은 하나만 허용한다. 재발급할 때 기존 Token을 `REVOKED`로 완료 처리해 새 Token과 구분한다.

## 12. `member_session_revocation`

회원 탈퇴 또는 세션 폐기 시 Access Token의 `sid`를 만료 시각까지 기록한다. 이 테이블은 회원 FK를 두지 않아 회원 데이터가 물리 삭제된 뒤에도 기존 Access Token을 거부할 수 있다. 같은 `sid`를 다시 기록하면 최초 폐기 시각과 최장 만료 시각을 보존한다.

## 13. 1차 확장 V3~V5 데이터 계약

### 13.1 V3 `favorite`

회원이 맛집을 찜한 현재 상태만 저장하는 관계 테이블이다. 논리 삭제 열을 두지 않으며 해제는 행을 물리 삭제한다.

| 컬럼 | SQL 타입 | Null | 기본값 | 제약조건 | 설명 |
|---|---|---:|---|---|---|
| `member_id` | `uuid` | NN | 없음 | PK 일부, FK → `member_account.id` | 찜한 회원 |
| `restaurant_id` | `uuid` | NN | 없음 | PK 일부, FK → `restaurant.id` | 찜한 맛집 |
| `favorited_at` | 시간 | NN | `CURRENT_TIMESTAMP` |  | 최초 찜 생성 시각 |

복합 PK `(member_id, restaurant_id)`가 회원별 중복 찜을 원자적으로 막는다. `member_id` FK는 `ON DELETE CASCADE`, `restaurant_id` FK는 `ON DELETE RESTRICT`다.

### 13.2 V3 `recent_restaurant_view`

회원별 맛집 최근 본 기록이다. 한 회원과 맛집 조합은 하나만 유지하고, 공개 맛집 상세의 성공 후 Command에서 `last_viewed_at`을 갱신한다. 별도 생성 시각·논리 삭제 열은 두지 않는다.

| 컬럼 | SQL 타입 | Null | 기본값 | 제약조건 | 설명 |
|---|---|---:|---|---|---|
| `member_id` | `uuid` | NN | 없음 | PK 일부, FK → `member_account.id` | 조회한 회원 |
| `restaurant_id` | `uuid` | NN | 없음 | PK 일부, FK → `restaurant.id` | 조회한 맛집 |
| `last_viewed_at` | 시간 | NN | `CURRENT_TIMESTAMP` |  | 마지막 성공 상세 조회 시각 |

복합 PK `(member_id, restaurant_id)`를 upsert 충돌 키로 사용한다. `member_id` FK는 `ON DELETE CASCADE`, `restaurant_id` FK는 `ON DELETE RESTRICT`다.

### 13.3 V4 `restaurant` 좌표 열

`V4__add_restaurant_coordinates.sql`은 기존 행을 변경하지 않고 다음 nullable 열을 추가한다. 두 값은 항상 함께 저장하거나 함께 `NULL`이어야 하며, 좌표가 없는 맛집은 일반 공개 조회에는 계속 남고 지도 조회에서만 제외한다.

| 컬럼 | SQL 타입 | Null | 기본값 | 제약조건 | 설명 |
|---|---|---:|---|---|---|
| `latitude` | `numeric(9,6)` | Yes | `NULL` | `-90..90`, longitude와 null 쌍 | WGS84 위도 |
| `longitude` | `numeric(9,6)` | Yes | `NULL` | `-180..180`, latitude와 null 쌍 | WGS84 경도 |

### 13.4 V5 `creator` 상세 표시 열

`V5__add_creator_detail_display_fields.sql`은 관리자가 마지막으로 확인해 저장한 채널 표시 정보만 추가한다. 구독자 수·실시간 외부 조회·표시 정보 이력은 이 범위에 저장하지 않는다.

| 컬럼 | SQL 타입 | Null | 기본값 | 제약조건 | 설명 |
|---|---|---:|---|---|---|
| `profile_image_url` | `varchar(2048)` | Yes | `NULL` | 비어 있지 않은 HTTPS URL | 채널 프로필 이미지 |
| `description` | `text` | Yes | `NULL` | 빈 문자열 금지 | 채널 소개 |
| `handle` | `varchar(255)` | Yes | `NULL` | 빈 문자열 금지 | 저장된 채널 handle; 고유 식별자로 사용하지 않음 |

기존 `channel_name`, `channel_url`과 위 세 필드는 공개 상세 응답의 `channelName`, `channelUrl`, `profileImageUrl`, `description`, `handle`에 대응한다.

## 14. Redis 경계

`AdminRefreshToken`은 Redis 8.8에만 저장한다. PostgreSQL `admin_account.id` 문자열을 Redis 값의 관리자 참조로 사용하되 DB FK 같은 원자성은 제공하지 않는다. Redis 키·검증값·14일 TTL·회전·재사용 탐지와 로그인 실패 제한은 [관리자 인증 API](../api/admin/authentication-api.md)와 [보안 경계](../../06-architecture/security-boundary.md)의 확정 계약을 따른다. Redis 구조는 이 문서의 PostgreSQL 스키마와 Flyway 대상이 아니다.

회원 세션은 `auth:member:` namespace만 사용하며 관리자 `auth:refresh:` 키와 공유하지 않는다. 세션 ID별 Refresh Token 해시와 회원별 정렬 집합을 함께 저장해 최대 세 세션을 유지한다. 회전과 재사용 탐지는 Lua 스크립트로 원자 처리하며 Redis를 읽거나 쓰지 못하면 발급·재발급을 허용하지 않는다.
