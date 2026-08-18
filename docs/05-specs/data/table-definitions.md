---
status: accepted
related_documents:
  - physical-data-model.md
  - constraint-mapping.md
  - index-strategy.md
  - migration-plan.md
  - second-expansion-data-contract.md
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

## 4. `admin_account` (전환 중 legacy)

V1에 존재하는 사전 발급 관리자 계정이다. 단일 계정 전환의 확장 단계에서 읽기 호환과 검증을 위해 유지하고, 계약 단계에서 참조 이전 증명을 통과한 뒤 제거한다. 신규 계정 원천으로 사용하지 않는다.

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

### 4.1 `admin_account_migration_map` (전환 staging)

통합 계정 전환에만 사용하는 일회성 매핑 테이블이다. 확장 단계에서 만들고 공동 승인된 입력을 적재한 뒤, 계약 증명을 통과하면 `admin_account`와 함께 제거한다. 신규 계정 원장이나 런타임 인증 조회에 사용하지 않는다.

| 컬럼 | SQL 타입 | Null | 기본값 | 키·제약 | 설명 |
|---|---|---:|---|---|---|
| `admin_account_id` | `uuid` | NN | 없음 | PK, FK → `admin_account.id` | legacy 관리자 ID |
| `normalized_email` | `varchar(254)` | NN | 없음 | UK, 빈 값·이메일 형식 금지 | 회원가입과 같은 규칙으로 정규화한 승인 이메일 |
| `migration_disposition` | `varchar(24)` | NN | 없음 | `MIGRATE_ACTIVE/PRESERVE_INACTIVE` CHECK | legacy `active`와 일치하는 승인 전환 구분 |
| `member_account_id` | `uuid` | Yes | `NULL` | UK, 값이 있으면 FK → `member_account.id` | 검증·복사 뒤 확정한 통합 계정 ID |
| `approval_record_id` | `varchar(100)` | NN | 없음 | 빈 값 금지 | 접근 통제된 운영 변경 기록 식별자 |
| `created_at` | 시간 | NN | `CURRENT_TIMESTAMP` |  | 적재 시각 |

실제 이메일이나 입력 파일을 저장소에 커밋하지 않는다. 입력 파일 SHA-256·승인자·승인 시각·행 수는 `approval_record_id`가 가리키는 변경 기록에 남기고, 역할 부여 전 전체 legacy 관리자에 대해 누락·중복·정규화 충돌·동일 회원 수렴이 0건인지 검증한다. `MIGRATE_ACTIVE`는 `active=true`, `PRESERVE_INACTIVE`는 `active=false`와 일치해야 한다. 활성 전환은 최종 `ACTIVE/ADMIN`, 비활성 보존은 기존 회원 불변 또는 신규 `DISABLED/MEMBER`만 허용한다.

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
| `admin_account_id` | `uuid` | NN | 없음 | 전환 전 FK → `admin_account`; 계약 후 회원 UUID FK → `member_account` | 발급 행위자 |
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

일반 회원과 관리자가 공유하는 단일 계정 테이블이다. 이메일은 고유하고, 비밀번호 원문이나 Refresh Token은 저장하지 않는다. 기존 열에 다음 역할 열을 전진 마이그레이션으로 추가한다.

| 컬럼 | SQL 타입 | Null | 기본값 | 키·제약 | 설명 |
|---|---|---:|---|---|---|
| `role` | `varchar(16)` | NN | `'MEMBER'` | `MEMBER/ADMIN` | 통합 로그인 RBAC 역할 |

공개 회원가입은 요청 역할을 받지 않고 기본값 `MEMBER`만 생성한다. `ADMIN` 발급·변경·회수는 승인된 운영 절차만 수행한다. 상태는 `PENDING_VERIFICATION`, `ACTIVE`, `DELETION_PENDING`, `DISABLED`이며 이메일 인증 시각·삭제 요청 시각 조합을 CHECK로 강제한다. 역할·상태·비밀번호 변경은 Redis 활성 세션 전체 폐기와 결합한다.

## 11. `member_action_token`

이메일 인증과 비밀번호 재설정은 SHA-256 해시만 저장하는 1회용 Token을 사용한다. `EMAIL_VERIFICATION` 원문은 32자 문자 집합에서 CSPRNG로 생성한 8자 코드이고, `PASSWORD_RESET` 원문은 기존 고엔트로피 불투명 값이다. 형식 차이는 원문 생성·입력 검증 경계에만 적용하며 테이블에는 두 용도 모두 동일한 고정 길이 해시를 저장한다. Token은 `ISSUED`, `USED`, `REVOKED` 상태와 완료 시각을 가지며, `(member_id, purpose)`별 `ISSUED` Token은 하나만 허용한다. 재발급할 때 기존 Token을 `REVOKED`로 완료 처리해 새 Token과 구분한다.

## 12. `member_session_revocation`

회원 탈퇴 또는 세션 폐기 시 Access Token의 `sid`를 만료 시각까지 기록한다. 이 테이블은 회원 FK를 두지 않아 회원 데이터가 물리 삭제된 뒤에도 기존 Access Token을 거부할 수 있다. 같은 `sid`를 다시 기록하면 최초 폐기 시각과 최장 만료 시각을 보존한다.

## 13. V3 회원 인증 하드닝 데이터 계약

### 13.1 `member_action_mail_outbox`

회원가입·재설정 요청의 `202 Accepted` 응답과 실제 메일 전송을 분리한다. Action Token 원문은 AES-GCM으로 암호화한 `encrypted_token`과 12-byte nonce만 보관하며, 키 식별자는 `encryption_key_id`로 기록해 키 회전 뒤에도 복호화할 키를 선택한다. 암호화·복호화의 AAD는 `member_action_token_id`와 `purpose`를 결합한 UTF-8 값으로 고정한다. 수신자는 Action Token의 회원 조인으로만 결정하고, 아웃박스에 별도 회원 ID나 이메일을 저장하지 않는다. dispatcher는 `PENDING` 행을 잠금·재시도하고 성공한 행만 `SENT`와 `sent_at`으로 완료한다. Action Token이 삭제되면 CASCADE로 함께 삭제한다.

| 컬럼 | SQL 타입 | Null | 제약조건 | 설명 |
|---|---|---:|---|---|
| `id` | `uuid` | NN | PK | 아웃박스 식별자 |
| `member_action_token_id` | `uuid` | NN | UK, FK → `member_action_token.id`, CASCADE | 발급 Action Token |
| `purpose` | `varchar(32)` | NN | `EMAIL_VERIFICATION/PASSWORD_RESET` | 전송할 Action Token 용도 |
| `encrypted_token` | `bytea` | NN | 16 byte 초과 | AES-GCM 암호문 |
| `encryption_nonce` | `bytea` | NN | 정확히 12 byte | AES-GCM nonce |
| `encryption_key_id` | `varchar(64)` | NN | 빈 값 금지 | 암호화 키 식별자 |
| `status` | `varchar(16)` | NN | `PENDING/SENT/FAILED/CANCELLED` | 전송 상태 |
| `attempt_count`, `next_attempt_at`, `locked_until`, `sent_at`, `created_at` | 수·시간 | 조건부 | 상태와 쌍 | 재시도·잠금·성공·생성 시각 |

### 13.2 `member_deletion_job`

`DELETION_PENDING` 전환과 같은 트랜잭션에서 회원 ID별 작업 하나를 upsert한다. 작업자는 15분 간격으로 개인정보·인증 Token·개인화 관계를 정리하고 계정을 물리 삭제한다. 성공하면 작업 행도 삭제하며, 원문 이메일·비밀번호·Token은 저장하지 않는다.

| 컬럼 | SQL 타입 | Null | 제약조건 | 설명 |
|---|---|---:|---|---|
| `member_id` | `uuid` | NN | PK, 회원 FK 없음 | 삭제 대상 식별자 |
| `requested_at`, `next_attempt_at`, `last_attempt_at` | 시간 | 마지막만 Yes |  | 접수·재시도 시각 |
| `attempt_count` | `integer` | NN | 0 이상 | 재시도 횟수 |

### 13.3 `member_session_revocation_recovery`

Redis 세션 변경 뒤 PostgreSQL `sid` 폐기 표식을 기록하지 못한 경우의 보상 작업이다. `sid`, 폐기·만료 시각과 재시도 일정만 보관한다. 복구 성공 시 행을 삭제하며, 만료 전에 완료하지 못한 작업은 운영 경보 대상이다.

| 컬럼 | SQL 타입 | Null | 제약조건 | 설명 |
|---|---|---:|---|---|
| `session_id` | `uuid` | NN | PK | 복구할 `sid` |
| `revoked_at`, `expires_at` | 시간 | NN | `expires_at > revoked_at` | 폐기 표식 값 |
| `next_attempt_at`, `last_attempt_at`, `attempt_count` | 시간·수 | 마지막만 Yes | 횟수 0 이상 | 재시도 상태 |

## 14. 1차 확장 V4~V6 데이터 계약

### 14.1 V4 `favorite`

회원이 맛집을 찜한 현재 상태만 저장하는 관계 테이블이다. 논리 삭제 열을 두지 않으며 해제는 행을 물리 삭제한다.

| 컬럼 | SQL 타입 | Null | 기본값 | 제약조건 | 설명 |
|---|---|---:|---|---|---|
| `member_id` | `uuid` | NN | 없음 | PK 일부, FK → `member_account.id` | 찜한 회원 |
| `restaurant_id` | `uuid` | NN | 없음 | PK 일부, FK → `restaurant.id` | 찜한 맛집 |
| `favorited_at` | 시간 | NN | `CURRENT_TIMESTAMP` |  | 최초 찜 생성 시각 |

복합 PK `(member_id, restaurant_id)`가 회원별 중복 찜을 원자적으로 막는다. `member_id` FK는 `ON DELETE CASCADE`, `restaurant_id` FK는 `ON DELETE RESTRICT`다.

### 14.2 V4 `recent_restaurant_view`

회원별 맛집 최근 본 기록이다. 한 회원과 맛집 조합은 하나만 유지하고, 공개 맛집 상세의 성공 후 Command에서 `last_viewed_at`을 갱신한다. 별도 생성 시각·논리 삭제 열은 두지 않는다.

| 컬럼 | SQL 타입 | Null | 기본값 | 제약조건 | 설명 |
|---|---|---:|---|---|---|
| `member_id` | `uuid` | NN | 없음 | PK 일부, FK → `member_account.id` | 조회한 회원 |
| `restaurant_id` | `uuid` | NN | 없음 | PK 일부, FK → `restaurant.id` | 조회한 맛집 |
| `last_viewed_at` | 시간 | NN | `CURRENT_TIMESTAMP` |  | 마지막 성공 상세 조회 시각 |

복합 PK `(member_id, restaurant_id)`를 upsert 충돌 키로 사용한다. upsert Command는 회원별 최신 50개 상한만 정리하고, 30일 경과 행은 회원 조회와 독립된 주기 cleanup Command가 물리 삭제한다. `member_id` FK는 `ON DELETE CASCADE`, `restaurant_id` FK는 `ON DELETE RESTRICT`다.

### 14.3 V5 `restaurant` 좌표 열

`V2__add_expansion_1_schema.sql`(V5 구간, [마이그레이션 계획](migration-plan.md#9-1차-확장-마이그레이션-구성-통합-이전-구간별-기록))은 기존 행을 변경하지 않고 다음 nullable 열을 추가한다. 두 값은 항상 함께 저장하거나 함께 `NULL`이어야 하며, 좌표가 없는 맛집은 일반 공개 조회에는 계속 남고 지도 조회에서만 제외한다.

| 컬럼 | SQL 타입 | Null | 기본값 | 제약조건 | 설명 |
|---|---|---:|---|---|---|
| `latitude` | `numeric(9,6)` | Yes | `NULL` | `-90..90`, longitude와 null 쌍 | WGS84 위도 |
| `longitude` | `numeric(9,6)` | Yes | `NULL` | `-180..180`, latitude와 null 쌍 | WGS84 경도 |

### 14.4 V6 `creator` 상세 표시 열

`V2__add_expansion_1_schema.sql`(V6 구간, [마이그레이션 계획](migration-plan.md#9-1차-확장-마이그레이션-구성-통합-이전-구간별-기록))은 관리자가 마지막으로 확인해 저장한 채널 표시 정보만 추가한다. 구독자 수·실시간 외부 조회·표시 정보 이력은 이 범위에 저장하지 않는다.

| 컬럼 | SQL 타입 | Null | 기본값 | 제약조건 | 설명 |
|---|---|---:|---|---|---|
| `profile_image_url` | `varchar(2048)` | Yes | `NULL` | 비어 있지 않은 HTTPS URL | 채널 프로필 이미지 |
| `description` | `text` | Yes | `NULL` | 빈 문자열 금지 | 채널 소개 |
| `handle` | `varchar(255)` | Yes | `NULL` | 빈 문자열 금지 | 저장된 채널 handle; 고유 식별자로 사용하지 않음 |

기존 `channel_name`, `channel_url`과 위 세 필드는 공개 상세 응답의 `channelName`, `channelUrl`, `profileImageUrl`, `description`, `handle`에 대응한다.

## 15. 2차 확장 테이블

2차 확장은 `personal_collection`, `collection_restaurant`, `curation`, `curation_restaurant`, `submission`, `report`, `moderation_history`, `notification`, `idempotency_record`를 추가한다. 전체 컬럼·타입·PK·FK·CHECK와 비저장 개념은 [2차 확장 데이터 계약](second-expansion-data-contract.md)에 정의한다.

`PopularityMetric`, `PopularitySnapshot`, `NotificationPreference`, `DeviceToken`과 알림 Outbox는 만들지 않는다. 인기 조회는 기존 `favorite`를 집계하고 알림은 요청 상태 전이와 같은 트랜잭션에서 `notification`에 직접 저장한다.

## 16. Redis 경계

Refresh Token 세션은 역할과 무관하게 Redis 8.8 `auth:session:` namespace에만 저장하고 `member_account.id`를 계정 참조로 사용한다. 세션 ID별 Token 해시와 계정별 정렬 집합을 함께 저장하며 상한은 `MEMBER` 3개, `ADMIN` 1개다. 회전·재사용 탐지·전체 폐기는 원자 처리하고 Redis를 읽거나 쓰지 못하면 발급·재발급을 허용하지 않는다. 쿠키·키 상세는 후속 ADR-AUTH-007이 소유한다. 전환 cutover에서 legacy 관리자 Refresh 세션은 전부 무효화한다.

## 17. 3차 확장 AI 영상 추출 테이블

3차 확장 물리 테이블·컬럼·재사용 조회 인덱스·재시도/태그 롤백 provenance의 정본은 [3차 확장 AI 영상 추출 데이터 계약](third-expansion-ai-video-data-contract.md)의 표와 [`V4__create_third_expansion_ai_schema.sql`](../../../src/main/resources/db/migration/V4__create_third_expansion_ai_schema.sql)에 둔다.

| 테이블 | 역할 | 핵심 무결성 |
|---|---|---|
| `ai_extraction_job` | Webhook·관리자 AI 작업과 Worker 상태 | 입력·버전 조합 멱등성, 상태·lease·시각 조합 |
| `ai_extraction_temporary_input` | 관리자 보완 텍스트 암호문 임시 보관 | 작업 FK, 관리자 텍스트 경계, 종료 후 24시간 만료 |
| `ai_candidate_snapshot` | 버전별 후보와 근거 | 작업·버전 unique, JSON object/array·근거 Schema |
| `ai_candidate_tag_review` | 후보 태그 자동 판단·사후 보정 이력 | Snapshot FK, decision·actor·replacement 조합 |
| `tag_definition` | 통제 태그 정의·18개 초기 기준 데이터 | 코드 unique, 유형·상태·별칭·생성 근거 |
| `visit_tag` | 확정 Visit와 태그 연결 | `(visit_id, tag_definition_id)` unique, AI 근거·Snapshot provenance |
| `ai_extraction_attempt` | Provider 시도·오류·비용 메타데이터 | `(job_id, attempt_no)` unique, 결과·오류 조합 |
| `youtube_channel_watch` | YouTube 채널 감시·갱신 상태 | Creator·채널별 unique, 구독 상태 |

정식 Restaurant·Creator·Video·Visit 저장은 이 후보 테이블과 별도의 애플리케이션 원자성·외부 검증 규칙을 따른다. 후보가 실패하거나 외부 검증이 실패하면 정식 Entity는 0건이어야 한다.
